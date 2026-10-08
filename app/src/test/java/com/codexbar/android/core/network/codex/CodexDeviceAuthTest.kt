package com.codexbar.android.core.network.codex

import com.codexbar.android.di.NetworkModule
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CodexDeviceAuthTest {
    private val server = MockWebServer()
    private lateinit var auth: CodexDeviceAuth
    private val verifier = "synthetic-verifier-".repeat(3)

    @Before fun setup() {
        server.start()
        auth = CodexDeviceAuth(NetworkModule.provideCodexTokenOkHttpClient(), server.url("/"))
    }
    @After fun teardown() { server.shutdown() }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body).setHeader("Content-Type", "application/json"))
    }
    private fun code(seconds: Int = 900) = respond("""{"device_auth_id":"synthetic-device-id","user_code":"TEST-CODE","interval":"1","expires_in":$seconds}""")
    private fun grant(challenge: String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))) =
        respond("""{"authorization_code":"synthetic-code","code_verifier":"$verifier","code_challenge":"$challenge"}""")

    @Test fun `pending authorization exchanges PKCE code without publishing credentials`() = runBlocking {
        code()
        respond("{}", 404)
        grant()
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"https://api.openai.com/auth":{"chatgpt_account_id":"synthetic-account"}}""".toByteArray())
        respond("""{"access_token":"header.$claims.signature","refresh_token":"synthetic-refresh","token_type":"Bearer"}""")
        val challenge = auth.requestCode()
        assertEquals("TEST-CODE", challenge.userCode)
        assertFalse(challenge.toString().contains("synthetic-device-id"))
        val credential = auth.awaitCredential(challenge)
        assertEquals("synthetic-account", credential.accountId)
        assertEquals("synthetic-refresh", credential.refreshToken)
        assertEquals("/api/accounts/deviceauth/usercode", server.takeRequest().path)
        assertEquals("/api/accounts/deviceauth/token", server.takeRequest().path)
        server.takeRequest()
        val exchange = server.takeRequest()
        assertEquals("/oauth/token", exchange.path)
        val body = exchange.body.readUtf8()
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code_verifier=$verifier"))
        assertTrue(body.contains("redirect_uri=https%3A%2F%2Fauth.openai.com%2Fdeviceauth%2Fcallback"))
    }

    @Test fun `denial and expiry terminate without token exchange`() = runBlocking {
        for (error in listOf("access_denied", "expired_token")) {
            code(); respond("""{"error":"$error","error_description":"synthetic-secret"}""", 400)
            val failure = runCatching { auth.awaitCredential(auth.requestCode()) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertFalse(failure!!.message!!.contains("synthetic-secret"))
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun `slow down increases polling interval`() = runBlocking {
        code(); respond("""{"error":"slow_down"}""", 429); respond("""{"error":"access_denied"}""", 400)
        val challenge = auth.requestCode()
        server.takeRequest()
        val job = async { runCatching { auth.awaitCredential(challenge) } }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        assertNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(6, TimeUnit.SECONDS) })
        assertTrue(job.await().exceptionOrNull() is IOException)
    }

    @Test fun `deadline and cancellation stop polling`() = runBlocking {
        code(1)
        val failure = runCatching { auth.awaitCredential(auth.requestCode()) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(1, server.requestCount)
        code()
        val challenge = auth.requestCode()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch { auth.awaitCredential(challenge) }
        withTimeout(5000) { while (server.requestCount < 3) delay(10) }
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertEquals(3, server.requestCount)
    }

    @Test fun `malformed PKCE cannot exchange tokens`() = runBlocking {
        code(); grant("mismatch")
        val failure = runCatching { auth.awaitCredential(auth.requestCode()) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(2, server.requestCount)
    }

    @Test fun `oversized response and redirects are rejected without leaking remote text`() = runBlocking {
        respond("synthetic-secret".repeat(6000))
        assertTrue(runCatching { auth.requestCode() }.exceptionOrNull() is IOException)
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/must-not-follow")))
        val failure = runCatching { auth.requestCode() }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(failure!!.message!!.contains("synthetic-secret"))
        assertEquals(2, server.requestCount)
        val client = NetworkModule.provideCodexTokenOkHttpClient()
        assertTrue(client.interceptors.isEmpty())
        assertFalse(client.retryOnConnectionFailure)
    }
}
