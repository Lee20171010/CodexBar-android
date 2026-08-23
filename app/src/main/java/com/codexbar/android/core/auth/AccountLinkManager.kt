package com.codexbar.android.core.auth

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import com.codexbar.android.core.network.oauth.DeviceAuthDto
import com.codexbar.android.core.network.oauth.GitHubDeviceAuthService
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class AccountLinkManager @Inject constructor(
    private val codexDeviceAuthService: CodexDeviceAuthService,
    private val gitHubDeviceAuthService: GitHubDeviceAuthService
) {

    suspend fun requestDeviceCode(service: AiService): DeviceAuthSession {
        return when (service) {
            AiService.CODEX -> requestCodexDeviceCode()
            AiService.COPILOT -> requestCopilotDeviceCode()
            else -> throw UnsupportedOperationException(
                "${service.displayName} does not expose a supported Android device-code flow."
            )
        }
    }

    suspend fun completeDeviceCode(session: DeviceAuthSession): Credential {
        return when (session.service) {
            AiService.CODEX -> completeCodexDeviceCode(session)
            AiService.COPILOT -> completeCopilotDeviceCode(session)
            else -> throw UnsupportedOperationException(
                "${session.service.displayName} does not expose a supported Android device-code flow."
            )
        }
    }

    private suspend fun requestCodexDeviceCode(): DeviceAuthSession {
        val response = codexDeviceAuthService.requestUserCode(
            DeviceAuthDto.CodexUserCodeRequest(clientId = CodexDto.CODEX_CLIENT_ID)
        )
        if (!response.isSuccessful) {
            throw IOException("Codex device-code request failed with HTTP ${response.code()}")
        }

        val body = response.body() ?: throw IOException("Codex device-code response was empty")
        val userCode = body.effectiveUserCode
            ?: throw IOException("Codex device-code response did not contain a user code")

        return DeviceAuthSession(
            service = AiService.CODEX,
            verificationUrl = CodexDeviceAuthService.CODEX_DEVICE_VERIFICATION_URL,
            userCode = userCode,
            deviceCode = body.deviceAuthId,
            intervalSeconds = safePollIntervalSeconds(body.intervalSeconds),
            expiresAtEpochMs = System.currentTimeMillis() + CODEX_DEVICE_EXPIRY_MS
        )
    }

    private suspend fun completeCodexDeviceCode(session: DeviceAuthSession): Credential.CodexCredential {
        val deadline = session.expiresAtEpochMs
        while (System.currentTimeMillis() < deadline) {
            val response = retryDnsFailureUntil(
                deadlineEpochMs = deadline,
                intervalSeconds = session.intervalSeconds
            ) {
                codexDeviceAuthService.pollForAuthorizationCode(
                    DeviceAuthDto.CodexTokenPollRequest(
                        deviceAuthId = session.deviceCode,
                        userCode = session.userCode
                    )
                )
            }

            if (response.isSuccessful) {
                val code = response.body()
                    ?: throw IOException("Codex authorization-code response was empty")
                val tokenResponse = retryDnsFailureUntil(
                    deadlineEpochMs = deadline,
                    intervalSeconds = session.intervalSeconds
                ) {
                    codexDeviceAuthService.exchangeAuthorizationCode(
                        code = code.authorizationCode,
                        clientId = CodexDto.CODEX_CLIENT_ID,
                        codeVerifier = code.codeVerifier
                    )
                }
                if (!tokenResponse.isSuccessful) {
                    throw IOException("Codex token exchange failed with HTTP ${tokenResponse.code()}")
                }
                val tokens = tokenResponse.body()
                    ?: throw IOException("Codex token exchange response was empty")
                return Credential.CodexCredential(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    accountId = codexAccountId(tokens.idToken, tokens.accessToken),
                    expiresAt = tokens.expiresIn
                        ?.takeIf { it > 0 }
                        ?.let { Instant.now().plusSeconds(it.toLong()) }
                        ?: codexTokenExpiresAt(tokens.accessToken)
                )
            }

            when (response.code()) {
                403, 404 -> delayUntilNextAttempt(deadline, session.intervalSeconds)
                else -> throw IOException("Codex device-code polling failed with HTTP ${response.code()}")
            }
        }

        throw IOException("Codex device-code login timed out")
    }

    private suspend fun <T> retryDnsFailureUntil(
        deadlineEpochMs: Long,
        intervalSeconds: Long,
        request: suspend () -> T
    ): T {
        while (true) {
            try {
                return request()
            } catch (error: Exception) {
                if (!error.hasUnknownHostCause() || System.currentTimeMillis() >= deadlineEpochMs) {
                    throw error
                }
                delayUntilNextAttempt(deadlineEpochMs, intervalSeconds)
            }
        }
    }

    private suspend fun delayUntilNextAttempt(
        deadlineEpochMs: Long,
        intervalSeconds: Long
    ) {
        val remainingMillis = (deadlineEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remainingMillis > 0L) {
            delay(minOf(pollDelayMillis(intervalSeconds), remainingMillis))
        }
    }

    private suspend fun requestCopilotDeviceCode(): DeviceAuthSession {
        val response = gitHubDeviceAuthService.requestDeviceCode()
        if (!response.isSuccessful) {
            throw IOException("GitHub device-code request failed with HTTP ${response.code()}")
        }

        val body = response.body() ?: throw IOException("GitHub device-code response was empty")
        return DeviceAuthSession(
            service = AiService.COPILOT,
            verificationUrl = trustedGitHubDeviceVerificationUrl(body.verificationUriComplete)
                ?: trustedGitHubDeviceVerificationUrl(body.verificationUri)
                ?: GitHubDeviceAuthService.GITHUB_DEVICE_VERIFICATION_URL,
            userCode = body.userCode,
            deviceCode = body.deviceCode,
            intervalSeconds = safePollIntervalSeconds(body.interval.toLong()),
            expiresAtEpochMs = System.currentTimeMillis() + body.expiresIn * 1000L
        )
    }

    private suspend fun completeCopilotDeviceCode(session: DeviceAuthSession): Credential.CopilotCredential {
        var intervalSeconds = safePollIntervalSeconds(session.intervalSeconds)
        while (System.currentTimeMillis() < session.expiresAtEpochMs) {
            delay(pollDelayMillis(intervalSeconds))
            val response = retryDnsFailureUntil(
                deadlineEpochMs = session.expiresAtEpochMs,
                intervalSeconds = intervalSeconds
            ) {
                gitHubDeviceAuthService.pollForAccessToken(deviceCode = session.deviceCode)
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub token polling failed with HTTP ${response.code()}")
            }

            val body = response.body() ?: throw IOException("GitHub token response was empty")
            when (body.error) {
                null -> {
                    val token = body.accessToken
                        ?: throw IOException("GitHub token response did not contain an access token")
                    return Credential.CopilotCredential(accessToken = token)
                }
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds = safePollIntervalSeconds(
                    intervalSeconds + SLOW_DOWN_INCREMENT_SECONDS
                )
                "expired_token" -> throw IOException("GitHub device-code login expired")
                else -> throw IOException(body.errorDescription ?: "GitHub device-code login failed: ${body.error}")
            }
        }

        throw IOException("GitHub device-code login timed out")
    }

    companion object {
        const val CODEX_DEVICE_EXPIRY_MS = 15 * 60 * 1000L
        private const val SLOW_DOWN_INCREMENT_SECONDS = 5L
    }
}

data class DeviceAuthSession(
    val service: AiService,
    val verificationUrl: String,
    val userCode: String,
    val deviceCode: String,
    val intervalSeconds: Long,
    val expiresAtEpochMs: Long
)

internal fun safePollIntervalSeconds(intervalSeconds: Long): Long {
    return intervalSeconds.coerceIn(MIN_POLL_INTERVAL_SECONDS, MAX_SAFE_POLL_INTERVAL_SECONDS)
}

internal fun pollDelayMillis(intervalSeconds: Long): Long {
    return safePollIntervalSeconds(intervalSeconds) * MILLIS_PER_SECOND
}

private fun Throwable.hasUnknownHostCause(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is UnknownHostException) return true
        current = current.cause
    }
    return false
}

private const val MIN_POLL_INTERVAL_SECONDS = 5L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_SAFE_POLL_INTERVAL_SECONDS = Long.MAX_VALUE / MILLIS_PER_SECOND

internal fun trustedGitHubDeviceVerificationUrl(rawUrl: String?): String? {
    val url = rawUrl?.toHttpUrlOrNull() ?: return null
    return rawUrl.takeIf {
        url.isHttps &&
            url.host == "github.com" &&
            url.encodedPath == "/login/device"
    }
}
