package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.domain.repository.CredentialSession
import kotlinx.coroutines.CancellationException
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class CodexRepositoryImpl @Inject constructor(
    private val apiService: CodexApiService,
    private val tokenRefreshService: CodexTokenRefreshService
) : QuotaRepository {

    override suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError> {
        require(session.connection.service == AiService.CODEX)
        val credential = session.credential
            as? Credential.CodexCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.CODEX))

        return try {
            val response = apiService.getUsage(
                authorization = "Bearer ${credential.accessToken}",
                accountId = credential.accountId
            )

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapToQuotaInfo(body))
                }
                401 -> {
                    when (val refresh = refreshToken(credential, session)) {
                        is Result.Failure -> refresh
                        is Result.Success -> {
                            val refreshed = refresh.value
                            val retryResponse = apiService.getUsage(
                                authorization = "Bearer ${refreshed.accessToken}",
                                accountId = refreshed.accountId
                            )
                            if (retryResponse.isSuccessful) {
                                val body = retryResponse.body()
                                    ?: return Result.Failure(AppError.ParseError("Empty response body"))
                                Result.Success(mapToQuotaInfo(body))
                            } else {
                                Result.Failure(httpError(retryResponse.code()))
                            }
                        }
                    }
                }
                else -> Result.Failure(httpError(response.code()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    private suspend fun refreshToken(credential: Credential.CodexCredential, session: CredentialSession): Result<Credential.CodexCredential, AppError> {
        return try {
            val request = CodexDto.TokenRefreshRequest(refreshToken = credential.refreshToken)
            val response = tokenRefreshService.refreshToken(request)

            if (response.isSuccessful) {
                val body = response.body() ?: return Result.Failure(AppError.ParseError("Empty token response"))
                require(body.accessToken.isNotBlank() && body.accessToken.none { it.isWhitespace() || it.isISOControl() })
                require(body.refreshToken == null || (body.refreshToken.isNotBlank() && body.refreshToken.none { it.isWhitespace() || it.isISOControl() }))
                val newCredential = Credential.CodexCredential(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken ?: credential.refreshToken,
                    accountId = credential.accountId
                )
                session.replace(newCredential)
                Result.Success(newCredential)
            } else {
                // Keep the account available for reconnect; never delete a sibling or draft owner.
                val errorCode = runCatching {
                    Json.parseToJsonElement(response.errorBody()?.string().orEmpty()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                Result.Failure(if (errorCode == "invalid_grant" || errorCode in CodexDto.TERMINAL_ERROR_CODES)
                    AppError.AuthError(AiService.CODEX, isTerminal = true) else httpError(response.code()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.Failure(AppError.NetworkError("Token renewal connection failed. Try again."))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected token renewal response"))
        }
    }

    private fun httpError(code: Int): AppError = when (code) {
        401, 403 -> AppError.AuthError(AiService.CODEX, isTerminal = true)
        429 -> AppError.RateLimited
        in 500..599 -> AppError.ServiceUnavailable
        else -> AppError.NetworkError("HTTP $code")
    }

    private fun mapToQuotaInfo(response: CodexDto.UsageResponse): QuotaInfo {
        val windows = buildList {
            response.rateLimit?.primaryWindow?.let { window ->
                add(mapRateLimitWindow("primary", window))
            }
            response.rateLimit?.secondaryWindow?.let { window ->
                add(mapRateLimitWindow("secondary", window))
            }
        }

        return QuotaInfo(
            service = AiService.CODEX,
            windows = windows,
            extraUsage = null,
            tier = response.planType?.replaceFirstChar { it.uppercase() },
            fetchedAt = Instant.now()
        )
    }

    private fun mapRateLimitWindow(type: String, window: CodexDto.RateLimitWindow): UsageWindow {
        val label = when (window.limitWindowSeconds) {
            18000L -> "5-Hour"
            604800L -> "7-Day"
            else -> {
                val seconds = window.limitWindowSeconds ?: 0L
                if (seconds > 0) "${seconds / 3600}h" else type.replaceFirstChar { it.uppercase() }
            }
        }

        return UsageWindow(
            label = label,
            utilization = window.usedPercent / 100.0,
            resetsAt = window.resetAt?.let { Instant.ofEpochSecond(it) }
        )
    }

    companion object {
        fun parseBalance(element: kotlinx.serialization.json.JsonElement?): Double? {
            if (element == null) return null
            return when (element) {
                is JsonPrimitive -> {
                    element.doubleOrNull ?: element.content.toDoubleOrNull()
                }
                else -> null
            }
        }
    }
}
