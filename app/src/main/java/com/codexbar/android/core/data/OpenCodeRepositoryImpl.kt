package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountBalance
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.RetryAfter
import com.codexbar.android.core.network.opencode.OpenCodeApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

class OpenCodeRepositoryImpl(
    private val api: OpenCodeApiService,
    private val prefs: EncryptedPrefsManager
) : QuotaRepository {
    private val service = AiService.OPENCODE_GO

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefs.loadCredential(service) as? Credential.ProviderSecretCredential
            ?: return Result.Failure(AppError.CredentialNotFound(service))
        return fetchQuota(credential)
    }

    override suspend fun validateCredential(): Result<Unit, AppError> = fetchQuota().asValidation()

    override suspend fun validateCredential(credential: Credential): Result<Unit, AppError> {
        val typed = credential as? Credential.ProviderSecretCredential ?: return terminalAuthError()
        return fetchQuota(typed).asValidation()
    }

    private suspend fun fetchQuota(
        credential: Credential.ProviderSecretCredential
    ): Result<QuotaInfo, AppError> = coroutineScope {
        val cookie = credential.cookieHeaderOrNull() ?: return@coroutineScope terminalAuthError()
        val workspaceId = credential.accountReference?.trim().takeUnless(String?::isNullOrEmpty)?.let {
            it.workspaceIdOrNull() ?: return@coroutineScope Result.Failure(
                AppError.ParseError("Invalid OpenCode Go workspace override")
            )
        } ?: when (val result = resolveWorkspace(cookie)) {
            is Result.Success -> result.value
            is Result.Failure -> return@coroutineScope Result.Failure(result.error)
        }
        val balanceTask = async {
            delay(OPTIONAL_BALANCE_START_DELAY_MILLIS)
            fetchBalance(cookie, workspaceId)
        }
        try {
            val payloadResult = request {
                api.getUsage(
                    workspaceId,
                    cookie,
                    PAGE_USER_AGENT,
                    PAGE_ACCEPT
                )
            }
            val payload = when (payloadResult) {
                is Result.Success -> payloadResult.value
                is Result.Failure -> return@coroutineScope Result.Failure(payloadResult.error)
            }
            val usageResult = runCatching {
                OpenCodePayloadParser.parseSubscription(
                    payload = payload
                )
            }
            val usage = usageResult.getOrNull()
            val balance = if (usage != null) {
                val joined = withTimeoutOrNull(OPTIONAL_BALANCE_JOIN_GRACE_MILLIS) {
                    balanceTask.await()
                }
                when (joined) {
                    is Result.Success -> joined.value
                    else -> null
                }
            } else {
                val diagnostic = ": " + OpenCodePayloadParser.payloadDiagnostic(payload)
                val usageError = AppError.ParseError(
                    "Invalid OpenCode Go usage payload$diagnostic",
                    usageResult.exceptionOrNull()
                )
                val requiredBalance = when (val result = balanceTask.await()) {
                    is Result.Success -> result.value
                    is Result.Failure -> {
                        if (result.error is AppError.AuthError && result.error.isTerminal) {
                            return@coroutineScope Result.Failure(result.error)
                        }
                        null
                    }
                }
                requiredBalance ?: return@coroutineScope Result.Failure(usageError)
            }
            Result.Success(
                QuotaInfo(
                    service = service,
                    windows = usage?.windows.orEmpty(),
                    extraUsage = null,
                    balance = balance,
                    renewsAt = usage?.renewsAt,
                    fetchedAt = Instant.now()
                )
            )
        } finally {
            balanceTask.cancel()
        }
    }

    private suspend fun fetchBalance(
        cookie: String,
        workspaceId: String
    ): Result<AccountBalance?, AppError> {
        val workspace = request {
            api.getWorkspace(
                workspaceId,
                cookie,
                PAGE_USER_AGENT,
                PAGE_ACCEPT
            )
        }
        when (workspace) {
            is Result.Success -> workspace.value.let(OpenCodePayloadParser::parseZenBalance)?.let {
                return Result.Success(it)
            }
            is Result.Failure -> {
                val error = workspace.error
                if (error is AppError.RateLimited || error is AppError.AuthError && error.isTerminal) {
                    return Result.Failure(error)
                }
            }
        }

        val billing = request {
            api.getServerFunction(
                BILLING_SERVER_ID,
                cookie,
                ORIGIN,
                "$ORIGIN/workspace/$workspaceId",
                USER_AGENT,
                ACCEPT,
                serverInstance(),
                args = "[\"$workspaceId\"]"
            )
        }
        return when (billing) {
            is Result.Success -> Result.Success(OpenCodePayloadParser.parseBillingZenBalance(billing.value))
            is Result.Failure -> Result.Failure(billing.error)
        }
    }

    private suspend fun resolveWorkspace(cookie: String): Result<String, AppError> {
        val getResponse = request {
            api.getServerFunction(
                WORKSPACE_SERVER_ID,
                cookie,
                ORIGIN,
                REFERER,
                USER_AGENT,
                ACCEPT,
                serverInstance()
            )
        }
        when (getResponse) {
            is Result.Success -> getResponse.value.workspaceIdFromPayload()?.let { return Result.Success(it) }
            is Result.Failure -> return Result.Failure(getResponse.error)
        }
        val postResponse = request {
            api.postServerFunction(
                cookie,
                ORIGIN,
                REFERER,
                USER_AGENT,
                ACCEPT,
                WORKSPACE_SERVER_ID,
                serverInstance(),
                "[]".toRequestBody(JSON_MEDIA_TYPE)
            )
        }
        return when (postResponse) {
            is Result.Success -> postResponse.value.workspaceIdFromPayload()?.let { Result.Success(it) }
                ?: Result.Failure(AppError.ParseError("OpenCode Go workspace was not found"))
            is Result.Failure -> Result.Failure(postResponse.error)
        }
    }

    private suspend fun request(
        call: suspend () -> Response<ResponseBody>
    ): Result<String, AppError> = try {
        val response = call()
        val payload = (if (response.isSuccessful) response.body() else response.errorBody())?.string()
        if (payload?.isSignedOut() == true) return terminalAuthError()
        if (response.code() in 300..399 && response.headers()["Location"]?.isAuthRedirect() == true) {
            return terminalAuthError()
        }
        when (response.code()) {
            in 200..299 -> payload?.let { Result.Success(it) }
                ?: Result.Failure(AppError.ParseError("Empty response body"))
            401, 403 -> terminalAuthError()
            429 -> Result.Failure(AppError.RateLimited(RetryAfter.parseRetryAt(response.headers()["Retry-After"])))
            else -> Result.Failure(AppError.NetworkError("HTTP ${response.code()}: ${response.message()}"))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.Failure(AppError.NetworkError(error.message ?: "Network error", error))
    } catch (error: Exception) {
        Result.Failure(AppError.ParseError(error.message ?: "OpenCode Go request failed", error))
    }

    private fun Credential.ProviderSecretCredential.cookieHeaderOrNull(): String? {
        if (service != this@OpenCodeRepositoryImpl.service || kind != ProviderSecretKind.COOKIE_HEADER) return null
        return canonicalOpenCodeCookieOrNull(accessToken)
    }

    private fun String?.workspaceIdOrNull(): String? {
        val value = this?.trim() ?: return null
        WORKSPACE_ID.matchEntire(value)?.let { return value }
        return WORKSPACE_URL.matchEntire(value)?.groupValues?.get(1)
    }

    private fun String.workspaceIdFromPayload(): String? = WORKSPACE_ID_IN_PAYLOAD.find(this)
        ?.groupValues
        ?.get(1)
        ?.workspaceIdOrNull()
        ?: runCatching { Json.parseToJsonElement(this) }.getOrNull()?.findWorkspaceId()

    private fun JsonElement.findWorkspaceId(): String? = when (this) {
        is JsonObject -> values.firstNotNullOfOrNull { it.findWorkspaceId() }
        is JsonArray -> firstNotNullOfOrNull { it.findWorkspaceId() }
        is JsonPrimitive -> takeIf(JsonPrimitive::isString)?.content?.takeIf(WORKSPACE_ID::matches)
    }

    private fun String.isSignedOut(): Boolean = lowercase().let { body ->
        "not associated with an account" in body || "actor of type \"public\"" in body ||
            body.trimStart().startsWith("<") && (
                "signed out" in body || "sign in" in body || "login" in body ||
                    "<title>openauth</title>" in body || "unauthorized" in body ||
                    "unauthenticated" in body || "auth/authorize" in body
                )
    }

    private fun String.isAuthRedirect(): Boolean {
        val path = runCatching { java.net.URI(this).path }.getOrNull()?.lowercase()?.trimEnd('/') ?: return false
        return path == "/login" || path.startsWith("/login/") ||
            path == "/auth" || path.startsWith("/auth/") ||
            path == "/signin" || path.startsWith("/signin/") ||
            path == "/sign-in" || path.startsWith("/sign-in/") ||
            path == "/openauth" || path.startsWith("/openauth/")
    }

    private fun Result<QuotaInfo, AppError>.asValidation(): Result<Unit, AppError> = when (this) {
        is Result.Success -> Result.Success(Unit)
        is Result.Failure -> Result.Failure(error)
    }

    private fun terminalAuthError(): Result.Failure<AppError> = Result.Failure(
        AppError.AuthError(service, isTerminal = true)
    )

    private fun serverInstance(): String = "server-fn:${UUID.randomUUID()}"

    private companion object {
        const val WORKSPACE_SERVER_ID = "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f"
        const val BILLING_SERVER_ID = "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d"
        const val ORIGIN = "https://opencode.ai"
        const val REFERER = "$ORIGIN/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
        const val ACCEPT = "application/json, text/javascript, */*; q=0.01"
        const val PAGE_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        const val PAGE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        const val OPTIONAL_BALANCE_START_DELAY_MILLIS = 25L
        const val OPTIONAL_BALANCE_JOIN_GRACE_MILLIS = 250L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val WORKSPACE_ID = Regex("wrk_[A-Za-z0-9]{1,124}")
        val WORKSPACE_URL = Regex(
            "https://(?:www\\.)?opencode\\.ai/workspace/(wrk_[A-Za-z0-9]{1,124})(?:[/?#].*)?"
        )
        val WORKSPACE_ID_IN_PAYLOAD = Regex("(?:^|,|\\{)\\s*(?:\\\"id\\\"|id)\\s*:\\s*\\\"(wrk_[A-Za-z0-9]{1,124})\\\"")
    }
}

internal fun canonicalOpenCodeCookieOrNull(raw: String): String? {
    if (raw.length > MAX_COOKIE_HEADER_LENGTH || raw.any(Char::isISOControl)) return null
    val normalized = raw.trim().removePrefixIgnoreCase("Cookie:").trim()
    if (normalized.isEmpty()) return null
    val pairs = normalized.split(';').map(String::trim)
        .filter { it.substringBefore('=') in AUTH_COOKIE_NAMES }
    if (pairs.isEmpty() || pairs.any { !it.isValidCookiePair() }) return null
    return pairs.joinToString("; ")
}

private fun String.isValidCookiePair(): Boolean {
    val separator = indexOf('=')
    if (separator <= 0 || separator == lastIndex || !COOKIE_NAME.matches(substring(0, separator))) return false
    val value = substring(separator + 1)
    return if (value.startsWith('"')) {
        value.length >= 2 && value.endsWith('"') && value.substring(1, value.length - 1)
            .all(::isCookieValueCharacter)
    } else {
        value.all(::isCookieValueCharacter)
    }
}

private fun isCookieValueCharacter(value: Char): Boolean =
    value.code in 0x21..0x7e && value !in setOf('"', ',', ';', '\\')

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

private const val MAX_COOKIE_HEADER_LENGTH = 16_384
private val COOKIE_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private val AUTH_COOKIE_NAMES = setOf("auth", "__Host-auth")
