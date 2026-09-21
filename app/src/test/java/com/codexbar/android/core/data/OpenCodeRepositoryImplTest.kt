package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.opencode.OpenCodeApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private fun <T> runTest(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)

class OpenCodeRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var api: OpenCodeApiService
    private lateinit var repository: OpenCodeRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefsManager = mock(EncryptedPrefsManager::class.java)
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            )
            .build()
            .create(OpenCodeApiService::class.java)
        repository = OpenCodeRepositoryImpl(api, prefsManager)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `canonical cookie keeps only OpenCode Go auth cookies`() {
        assertEquals(
            "auth=a; __Host-auth=b",
            canonicalOpenCodeCookieOrNull("Cookie: theme=x; auth=a; __Host-auth=b")
        )
    }

    @Test
    fun `cookie rejects invalid or non auth cookies before request`() = runTest {
        listOf(
            "provider=google; theme=dark",
            "session=value\r\nX-Test: injected",
            "session=value;; theme=dark",
        ).forEach { header ->
            val result = repository.validateCredential(cookie(header))
            assertTrue(header, result is Result.Failure && result.error is AppError.AuthError)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `normal fetch maps rolling and monthly windows`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> MockResponse().setBody(
                    """{"rollingUsage":{"usagePercent":10,"resetInSec":60},"monthlyUsage":{"usagePercent":20,"resetInSec":120}}"""
                )
                "/workspace/wrk_test" -> MockResponse().setBody("""{"zenBalance":12.5}""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quota = repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test"))
            .successValue()

        assertEquals(listOf("5-Hour", "Monthly"), quota.windows.map { it.label })
        assertEquals(12.5, quota.balance?.amount ?: -1.0, 0.0001)
    }

    @Test
    fun `automatic workspace discovery feeds usage and balance requests`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.requestUrl?.queryParameter("id") ==
                    "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f" -> {
                    MockResponse().setBody("""{"id":"wrk_auto"}""")
                }
                request.path == "/workspace/wrk_auto/go" -> MockResponse().setBody(
                    """{"rollingUsage":{"usagePercent":10,"resetInSec":60}}"""
                )
                request.path == "/workspace/wrk_auto" -> MockResponse().setBody("""{"zenBalance":12.5}""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quota = repository.fetchForTest(cookie("auth=v")).successValue()
        val paths = List(3) { requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).path }

        assertEquals(listOf("5-Hour"), quota.windows.map { it.label })
        assertEquals(12.5, quota.balance?.amount ?: -1.0, 0.0001)
        assertTrue(paths.any { it?.startsWith("/_server") == true })
        assertTrue(paths.contains("/workspace/wrk_auto/go"))
        assertTrue(paths.contains("/workspace/wrk_auto"))
    }

    @Test
    fun `billing fallback supplies workspace balance`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/workspace/wrk_test/go" -> MockResponse().setBody(
                    """{"rollingUsage":{"usagePercent":10,"resetInSec":60}}"""
                )
                request.path == "/workspace/wrk_test" -> MockResponse().setBody("{}")
                request.requestUrl?.queryParameter("id") ==
                    "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d" -> {
                    MockResponse().setBody(
                        """${'$'}R[1]={customerID:"cus_test",balance:${'$'}R[2]=1250000000}"""
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quota = repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_test")).successValue()
        val requests = List(3) { requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }
        val billingRequest = requests.single {
            it.requestUrl?.queryParameter("id") ==
                "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d"
        }

        assertEquals(12.5, quota.balance?.amount ?: -1.0, 0.0001)
        assertEquals(
            "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d",
            billingRequest.requestUrl?.queryParameter("id")
        )
        assertEquals(listOf("wrk_test"), billingRequest.serverArgs())
    }

    @Test
    fun `balance succeeds without usage windows`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> MockResponse().setBody("not usage")
                "/workspace/wrk_test" -> MockResponse().setBody("""{"zenBalance":12.5}""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quota = repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_test")).successValue()

        assertTrue(quota.windows.isEmpty())
        assertEquals(12.5, quota.balance?.amount ?: -1.0, 0.0001)
    }

    @Test
    fun `invalid workspace override fails before request`() = runTest {
        val error = repository.fetchForTest(cookie("auth=v", workspaceId = "invalid")).failureValue()

        assertTrue(error is AppError.ParseError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fetch keeps quota when balance is unauthorized and starts requests concurrently`() = runTest {
        val balanceStarted = CountDownLatch(1)
        val balanceStartedBeforeUsageCompleted = AtomicBoolean()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> {
                    balanceStartedBeforeUsageCompleted.set(balanceStarted.await(5, TimeUnit.SECONDS))
                    MockResponse().setBody("""{"rollingUsage":{"usagePercent":10,"resetInSec":60}}""")
                }
                "/workspace/wrk_test" -> { balanceStarted.countDown(); MockResponse().setResponseCode(401) }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quota = repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test")).successValue()
        assertEquals(listOf("5-Hour"), quota.windows.map { it.label })
        assertNull(quota.balance)
        assertTrue(balanceStartedBeforeUsageCompleted.get())
    }

    @Test
    fun `fetch keeps quota when billing balance is forbidden or rate limited`() = runTest {
        enqueueGo("""{"rollingUsage":{"usagePercent":10,"resetInSec":60}}""")
        enqueueWorkspace("{}")
        server.enqueue(MockResponse().setResponseCode(429))

        val quota = repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test")).successValue()
        assertEquals(listOf("5-Hour"), quota.windows.map { it.label })
        assertNull(quota.balance)
    }

    @Test
    fun `fetch bounds optional balance wait after valid quota`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> MockResponse()
                    .setBody("""{"rollingUsage":{"usagePercent":10,"resetInSec":60}}""")
                else -> MockResponse().setResponseCode(500)
            }
        }

        val quota = withTimeout(2_000) {
            repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test")).successValue()
        }
        assertEquals(listOf("5-Hour"), quota.windows.map { it.label })
        assertNull(quota.balance)
    }

    @Test
    fun `usage parse failure returns terminal balance authentication error`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> MockResponse().setBody("not usage")
                "/workspace/wrk_test" -> MockResponse().setResponseCode(401)
                else -> MockResponse().setResponseCode(404)
            }
        }

        val error = repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_test")).failureValue()
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    @Test
    fun `valid usage waits at most upstream balance grace`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_test/go" -> MockResponse()
                    .setBody("""{"rollingUsage":{"usagePercent":10,"resetInSec":60}}""")
                "/workspace/wrk_test" -> MockResponse()
                    .setBodyDelay(1, TimeUnit.SECONDS)
                    .setBody("""{"zenBalance":12.5}""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val startedAt = System.nanoTime()
        val quota = withTimeout(2_000) {
            repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_test")).successValue()
        }
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue("elapsed=$elapsedMillis", elapsedMillis < 700)
        assertNull(quota.balance)
    }

    @Test
    fun `unauthorized response requires reconnection`() = runTest {
        enqueueGo("unauthorized", code = 401)
        enqueueWorkspace("unauthorized", code = 401)

        val error = repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test")).failureValue()
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    @Test
    fun `login HTML markers require reconnection instead of parse error`() = runTest {
        listOf("login", "sign in", "actor of type \"public\"", "auth/authorize").forEach { marker ->
            enqueueGo("<html>$marker</html>")
            enqueueWorkspace("unauthorized", code = 401)

            val error = repository.fetchForTest(cookie("auth=v", AiService.OPENCODE_GO, "wrk_test")).failureValue()
            assertTrue(marker, error is AppError.AuthError && error.isTerminal)
            assertFalse(marker, error is AppError.ParseError)
        }
    }

    @Test
    fun `auth redirect is terminal without following while ordinary redirect stays network error`() = runTest {
        val authTargetVisited = AtomicBoolean()
        val ordinaryTargetVisited = AtomicBoolean()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/workspace/wrk_auth/go" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/auth/login")
                "/workspace/wrk_other/go" -> MockResponse()
                    .setResponseCode(307)
                    .addHeader("Location", "/maintenance")
                "/auth/login" -> {
                    authTargetVisited.set(true)
                    MockResponse().setBody("login")
                }
                "/maintenance" -> {
                    ordinaryTargetVisited.set(true)
                    MockResponse().setBody("maintenance")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val authError = repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_auth")).failureValue()
        val ordinaryError = repository.fetchForTest(cookie("auth=v", workspaceId = "wrk_other")).failureValue()

        assertTrue(authError is AppError.AuthError && authError.isTerminal)
        assertTrue(ordinaryError is AppError.NetworkError)
        assertFalse(authTargetVisited.get())
        assertFalse(ordinaryTargetVisited.get())
    }

    @Test
    fun `parse error includes only safe diagnostic`() = runTest {
        val payload = """{"email":"private@x.invalid","token":"t","unexpected":{"detail":"Private Name"}}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/workspace/wrk_test/go" -> MockResponse().setBody(payload)
                request.path == "/workspace/wrk_test" -> MockResponse().setBody("{}")
                request.requestUrl?.queryParameter("id") ==
                    "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d" -> {
                    MockResponse().setBody("{}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val error = repository.fetchForTest(cookie("auth=1", AiService.OPENCODE_GO, "wrk_test"))
            .failureValue() as AppError.ParseError
        assertTrue(error.message.orEmpty().contains("response=json"))
        assertFalse(error.message.orEmpty().contains("private"))
    }

    @Test
    fun `workspace URL normalization extracts ID`() = runTest {
        enqueueGo("""{"rollingUsage":{"usagePercent":10,"resetInSec":60}}""")

        assertTrue(
            repository.fetchForTest(
                cookie("auth=v", AiService.OPENCODE_GO, "https://opencode.ai/workspace/wrk_test")
            ) is Result.Success
        )
        assertEquals("/workspace/wrk_test/go", server.takeRequest().path)
    }

    private fun cookie(
        value: String,
        service: AiService = AiService.OPENCODE_GO,
        workspaceId: String? = null
    ) = Credential.ProviderSecretCredential(
        service = service,
        kind = ProviderSecretKind.COOKIE_HEADER,
        accessToken = value,
        accountReference = workspaceId
    )

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }
    private fun enqueueWorkspace(body: String, code: Int = 200) = enqueue(body, code)
    private fun enqueueGo(body: String, code: Int = 200) = enqueue(body, code)

    private fun Result<QuotaInfo, AppError>.successValue(): QuotaInfo =
        (this as Result.Success).value

    private fun Result<QuotaInfo, AppError>.failureValue(): AppError =
        (this as Result.Failure).error

    private suspend fun OpenCodeRepositoryImpl.fetchForTest(
        credential: Credential.ProviderSecretCredential
    ): Result<QuotaInfo, AppError> {
        `when`(prefsManager.loadCredential(credential.service)).thenReturn(credential)
        return fetchQuota()
    }

    private fun RecordedRequest.serverArgs(): List<String> = Json.parseToJsonElement(
        requireNotNull(requestUrl?.queryParameter("args"))
    ).jsonArray.map { it.jsonPrimitive.content }
}
