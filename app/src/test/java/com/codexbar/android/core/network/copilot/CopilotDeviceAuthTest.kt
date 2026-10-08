package com.codexbar.android.core.network.copilot

import com.codexbar.android.di.NetworkModule
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CopilotDeviceAuthTest {
    private val server = MockWebServer()
    private lateinit var auth: CopilotDeviceAuth
    @Before fun setup() {
        server.start()
        auth = CopilotDeviceAuth(NetworkModule.provideCodexTokenOkHttpClient(), "synthetic-client", server.url("/"))
    }
    @After fun teardown() { server.shutdown() }
    private fun reply(text: String) { server.enqueue(MockResponse().setBody(text)) }
    private fun code(seconds: Int = 900, url: String = CopilotDeviceAuth.VERIFICATION_URL) =
        reply("""{"user_code":"TEST-CODE","device_code":"synthetic-device","interval":1,"expires_in":$seconds,"verification_uri":"$url"}""")

    @Test fun `registered client and pending flow yield token only after authorization`() = runBlocking {
        code()
        reply("""{"error":"authorization_pending"}""")
        reply("""{"access_token":"synthetic-token","token_type":"bearer"}""")
        val challenge = auth.requestCode()
        assertFalse(challenge.toString().contains("synthetic-device"))
        assertEquals("TEST-CODE", challenge.userCode)
        assertEquals("synthetic-token", auth.awaitCredential(challenge).accessToken)
        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("client_id=synthetic-client&scope=read%3Auser", request.body.readUtf8())
        val poll = server.takeRequest()
        assertEquals("/login/oauth/access_token", poll.path)
        assertTrue(poll.body.readUtf8().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
    }

    @Test fun `unregistered client untrusted verification URL denial and expiry fail closed`() = runBlocking {
        val unregistered = CopilotDeviceAuth(NetworkModule.provideCodexTokenOkHttpClient(), "", server.url("/"))
        assertTrue(runCatching { unregistered.requestCode() }.exceptionOrNull() is IOException)
        assertEquals(0, server.requestCount)
        code(url = "https://example.invalid/")
        assertTrue(runCatching { auth.requestCode() }.exceptionOrNull() is IOException)
        for (error in listOf("access_denied", "expired_token", "incorrect_client_credentials")) {
            code(); reply("""{"error":"$error","error_description":"synthetic-secret"}""")
            val failure = runCatching { auth.awaitCredential(auth.requestCode()) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertFalse(failure!!.message!!.contains("synthetic-secret"))
        }
        code(1)
        val challenge = auth.requestCode()
        val before = server.requestCount
        assertTrue(runCatching { auth.awaitCredential(challenge) }.exceptionOrNull() is IOException)
        assertEquals(before, server.requestCount)
    }

    @Test fun `slow down adds five seconds and cancellation stops active request`() = runBlocking {
        code(); reply("""{"error":"slow_down"}""")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val challenge = auth.requestCode()
        server.takeRequest()
        val job = launch { auth.awaitCredential(challenge) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        assertNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(6, TimeUnit.SECONDS) })
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }
}
