package com.codexbar.android.core.network.codex

import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.di.CodexTokenClient
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Codex's device-code protocol. Credentials stay in memory until quota validation succeeds. */
class CodexDeviceAuth internal constructor(
    private val client: OkHttpClient,
    private val base: HttpUrl,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    @Inject constructor(@CodexTokenClient client: OkHttpClient) : this(client, BASE.toHttpUrl())

    // Ordinary classes deliberately avoid generated toString() containing device/token secrets.
    class Challenge internal constructor(
        val userCode: String,
        internal val deviceId: String,
        internal val intervalMillis: Long,
        internal val deadline: Long
    )

    suspend fun requestCode(): Challenge {
        val response = post("api/accounts/deviceauth/usercode", jsonBody("client_id" to CodexDto.CODEX_CLIENT_ID))
        if (response.code != 200) throw IOException("Device-code login is unavailable. Check that your account allows device-code sign-in.")
        val body = response.body
        val userCode = body.text("user_code") ?: body.text("usercode")
        val id = body.text("device_auth_id")
        if (userCode.isNullOrBlank() || userCode.length > 64 || userCode.any(Char::isISOControl) ||
            id.isNullOrBlank() || id.length > 1024 || id.any(Char::isISOControl)) invalidResponse()
        val interval = body["interval"]?.let { body.text("interval")?.toLongOrNull() ?: invalidResponse() } ?: 5L
        val lifetime = body["expires_in"]?.let { body.text("expires_in")?.toLongOrNull() ?: invalidResponse() } ?: 900L
        if (interval !in 1..60 || lifetime !in 1..900) invalidResponse()
        return Challenge(userCode, id, interval * 1000, monotonicMillis() + lifetime * 1000)
    }

    suspend fun awaitCredential(challenge: Challenge): Credential.CodexCredential {
        val remaining = challenge.deadline - monotonicMillis()
        if (remaining <= 0) throw IOException("Device code expired. Start sign-in again.")
        return withTimeoutOrNull(remaining) {
            var interval = challenge.intervalMillis
            while (true) {
                delay(interval)
                val response = post("api/accounts/deviceauth/token", jsonBody(
                    "device_auth_id" to challenge.deviceId, "user_code" to challenge.userCode))
                when (response.body.text("error")) {
                    "access_denied" -> throw IOException("Sign-in was declined.")
                    "expired_token" -> throw IOException("Device code expired. Start sign-in again.")
                    "slow_down" -> { interval = (interval + 5000).coerceAtMost(60_000); continue }
                    "authorization_pending" -> continue
                }
                when (response.code) {
                    200 -> return@withTimeoutOrNull exchange(response.body)
                    403, 404 -> Unit // Pending statuses used by the official Codex device flow.
                    429 -> interval = (interval + 5000).coerceAtMost(60_000)
                    in 500..599 -> Unit
                    else -> throw IOException("Device-code sign-in failed. Start sign-in again.")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } ?: throw IOException("Device code expired. Start sign-in again.")
    }

    private suspend fun exchange(body: JsonObject): Credential.CodexCredential {
        val code = body.text("authorization_code")?.takeIf { it.isNotBlank() && it.length <= 8192 } ?: invalidResponse()
        val verifier = body.text("code_verifier")?.takeIf { it.length in 43..128 && it.all { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~" } }
            ?: invalidResponse()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        if (challenge != body.text("code_challenge")) invalidResponse()
        val form = FormBody.Builder().add("grant_type", "authorization_code")
            .add("client_id", CodexDto.CODEX_CLIENT_ID).add("code", code)
            .add("code_verifier", verifier).add("redirect_uri", "${BASE}deviceauth/callback").build()
        val response = post("oauth/token", form)
        if (response.code != 200) throw IOException("Sign-in could not be completed. Start sign-in again.")
        fun token(name: String) = response.body.text(name)?.takeIf {
            it.isNotBlank() && it.length <= 32768 && it.none { c -> c.isWhitespace() || c.isISOControl() }
        } ?: invalidResponse()
        val access = token("access_token")
        val refresh = token("refresh_token")
        val type = response.body.text("token_type")
        if (type != null && !type.equals("Bearer", ignoreCase = true)) invalidResponse()
        // Claims come from the HTTPS token exchange, not browser/Intent input. They are hints;
        // the authenticated quota request remains the validation gate before account publication.
        val accountId = try {
            val payload = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(access.split('.')[1])))
            payload.jsonObject["https://api.openai.com/auth"]?.jsonObject?.text("chatgpt_account_id")
                ?.takeIf { it.length in 1..256 && it.none(Char::isISOControl) }
        } catch (_: Exception) { null }
        return Credential.CodexCredential(access, refresh, accountId)
    }

    private class Reply(val code: Int, val body: JsonObject)

    private suspend fun post(path: String, body: RequestBody): Reply = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(base.resolve(path)!!).post(body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Sign-in connection failed. Try again."))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val source = response.body?.source() ?: invalidResponse()
                        source.request(65_537)
                        if (source.buffer.size > 65_536) invalidResponse()
                        val text = source.readUtf8()
                        val json = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) {
                            if (response.isSuccessful) invalidResponse() else JsonObject(emptyMap())
                        }
                        if (continuation.isActive) continuation.resume(Reply(response.code, json))
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Unexpected sign-in response. Try again."))
                    }
                }
            }
        })
    }

    private fun jsonBody(vararg fields: Pair<String, String>): RequestBody =
        JsonObject(fields.associate { it.first to JsonPrimitive(it.second) }).toString()
            .toRequestBody("application/json".toMediaType())

    private fun JsonObject.text(name: String) = (get(name) as? JsonPrimitive)?.contentOrNull
    private fun invalidResponse(): Nothing = throw IOException("Unexpected sign-in response. Try again.")

    companion object {
        private const val BASE = "https://auth.openai.com/"
        const val VERIFICATION_URL = "https://auth.openai.com/codex/device"
    }
}
