package com.codexbar.android.core.network.copilot

import com.codexbar.android.BuildConfig
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.postDeviceAuth
import com.codexbar.android.di.CodexTokenClient
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** GitHub OAuth device flow, enabled only with this project's registered public client ID. */
class CopilotDeviceAuth internal constructor(
    private val client: OkHttpClient,
    private val clientId: String,
    private val base: HttpUrl = "https://github.com/".toHttpUrl(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    @Inject constructor(@CodexTokenClient client: OkHttpClient) : this(client, BuildConfig.COPILOT_CLIENT_ID)

    class Challenge internal constructor(
        val userCode: String,
        internal val deviceCode: String,
        internal val intervalMillis: Long,
        internal val deadline: Long
    )

    suspend fun requestCode(): Challenge {
        if (!clientId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) throw IOException("GitHub OAuth App registration is required.")
        val reply = post("login/device/code", "client_id" to clientId, "scope" to "read:user")
        if (reply.code != 200) failed()
        val body = reply.body
        val user = body.token("user_code", 64)
        val device = body.token("device_code", 1024)
        if (body.text("verification_uri") != VERIFICATION_URL) failed()
        val lifetime = body.text("expires_in")?.toLongOrNull() ?: failed()
        val interval = body.text("interval")?.toLongOrNull() ?: failed()
        if (lifetime !in 1..900 || interval !in 1..60) failed()
        return Challenge(user, device, interval * 1000, clock() + lifetime * 1000)
    }

    suspend fun awaitCredential(challenge: Challenge): Credential.CopilotCredential {
        val remaining = challenge.deadline - clock()
        if (remaining <= 0) failed()
        return withTimeoutOrNull(remaining) {
            var interval = challenge.intervalMillis
            while (true) {
                delay(interval)
                if (clock() >= challenge.deadline) failed()
                val reply = post("login/oauth/access_token", "client_id" to clientId,
                    "device_code" to challenge.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code")
                when (reply.body.text("error")) {
                    "authorization_pending" -> continue
                    "slow_down" -> { interval += 5000; continue }
                    null -> Unit
                    else -> failed()
                }
                when (reply.code) {
                    200 -> {
                        if (!reply.body.text("token_type").equals("bearer", ignoreCase = true)) failed()
                        return@withTimeoutOrNull Credential.CopilotCredential(reply.body.token("access_token", 32768))
                    }
                    429 -> interval += 5000
                    in 500..599 -> Unit
                    else -> failed()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } ?: failed()
    }

    private suspend fun post(path: String, vararg values: Pair<String, String>) = client.postDeviceAuth(
        base.resolve(path)!!, FormBody.Builder().apply { values.forEach { (key, value) -> add(key, value) } }.build())
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.token(key: String, max: Int) = text(key)?.takeIf {
        it.length in 1..max && it.none { c -> c.isWhitespace() || c.isISOControl() }
    } ?: failed()
    private fun failed(): Nothing = throw IOException("GitHub sign-in failed, was declined or expired. Start sign-in again.")

    companion object {
        const val VERIFICATION_URL = "https://github.com/login/device"
    }
}
