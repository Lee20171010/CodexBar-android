package com.codexbar.android.core.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.repository.CredentialSession
import kotlinx.coroutines.CancellationException
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import retrofit2.Retrofit

class CodexRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: CodexApiService
    private lateinit var tokenRefreshService: CodexTokenRefreshService
    private var credential: Credential? = null
    private suspend fun CodexRepositoryImpl.fetchQuota() = fetchQuota(
        CredentialSession(AccountConnection.create(AiService.CODEX), credential)
    )
    private lateinit var repository: CodexRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val testCredential = Credential.CodexCredential(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        accountId = "test-account-id"
    )

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        val contentType = "application/json".toMediaType()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(CodexApiService::class.java)

        tokenRefreshService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(CodexTokenRefreshService::class.java)

        credential = testCredential
        repository = CodexRepositoryImpl(apiService, tokenRefreshService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `native retry uses atomically published rotation and never Kotlin usage`() = runTest {
        val initial = testCredential.copy(lastRefresh = java.time.Instant.now())
        var published = false
        var calls = 0
        val session = CredentialSession(AccountConnection.create(AiService.CODEX), initial) { old, next ->
            assertEquals(initial, old)
            assertEquals("synthetic-next-access", next.accessToken)
            assertEquals("synthetic-next-refresh", next.refreshToken)
            published = true
            true
        }
        val native = org.mockito.Mockito.mock(com.codexbar.android.core.nativecli.NativeCodexBarClient::class.java) { invocation ->
            if (invocation.method.name == "fetchCodex") {
                val supplied = invocation.getArgument<Credential.CodexCredential>(0)
                calls++
                if (calls == 1) {
                    assertEquals(initial, supplied)
                    Result.Failure(AppError.AuthError(AiService.CODEX, true))
                } else {
                    assertTrue(published)
                    assertEquals(session.credential, supplied)
                    Result.Failure(AppError.RateLimited)
                }
            } else org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        mockWebServer.enqueue(MockResponse().setBody("""{"access_token":"synthetic-next-access","refresh_token":"synthetic-next-refresh"}"""))
        val result = CodexRepositoryImpl(apiService, tokenRefreshService, native).fetchQuota(session) as Result.Failure
        assertEquals(AppError.RateLimited, result.error)
        assertEquals(2, calls)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals("/oauth/token", mockWebServer.takeRequest().path)
    }

    @Test
    fun `draft refresh stays in request memory without overwriting saved credentials`() = runTest {
        val draft = CredentialSession(AccountConnection.create(AiService.CODEX), testCredential)
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setBody("""{"access_token":"synthetic-rotated","refresh_token":"synthetic-refresh-next"}"""))
        mockWebServer.enqueue(MockResponse().setBody("""{"plan_type":"pro"}"""))

        assertTrue(repository.fetchQuota(draft) is Result.Success)
        assertEquals("synthetic-rotated", draft.credential?.accessToken)
        assertEquals("synthetic-refresh-next", draft.credential?.refreshToken)
        assertEquals("Bearer test-access-token", mockWebServer.takeRequest().getHeader("Authorization"))
        mockWebServer.takeRequest()
        assertEquals("Bearer synthetic-rotated", mockWebServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `rejected generation publication cancels before retrying with late tokens`() = runTest {
        val request = CredentialSession(AccountConnection.create(AiService.CODEX), testCredential) { _, _ -> false }
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setBody("""{"access_token":"synthetic-late","refresh_token":"synthetic-late-refresh"}"""))

        val failure = runCatching { repository.fetchQuota(request) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(testCredential, request.credential)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `same-provider sessions send their own credentials and retain rejected accounts`() = runTest {
        val first = CredentialSession(AccountConnection.create(AiService.CODEX), testCredential)
        val secondCredential = testCredential.copy(accessToken = "synthetic-second", accountId = "synthetic-owner-two")
        val second = CredentialSession(AccountConnection.create(AiService.CODEX), secondCredential)
        mockWebServer.enqueue(MockResponse().setBody("""{"plan_type":"pro"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        assertTrue(repository.fetchQuota(first) is Result.Success)
        assertTrue(repository.fetchQuota(second) is Result.Failure)
        assertEquals("Bearer test-access-token", mockWebServer.takeRequest().getHeader("Authorization"))
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("Bearer synthetic-second", secondRequest.getHeader("Authorization"))
        assertEquals("synthetic-owner-two", secondRequest.getHeader("ChatGPT-Account-Id"))
        assertEquals(secondCredential, second.credential)
        assertEquals(testCredential, first.credential)
    }

    @Test
    fun `transient renewal and retry failures are not terminal auth failures`() = runTest {
        for ((code, expected) in listOf(429 to AppError.RateLimited::class, 503 to AppError.ServiceUnavailable::class)) {
            mockWebServer.enqueue(MockResponse().setResponseCode(401))
            mockWebServer.enqueue(MockResponse().setResponseCode(code))
            val session = CredentialSession(AccountConnection.create(AiService.CODEX), testCredential)
            assertEquals(expected, (repository.fetchQuota(session) as Result.Failure).error::class)
            assertEquals(testCredential, session.credential)
            mockWebServer.enqueue(MockResponse().setResponseCode(401))
            mockWebServer.enqueue(MockResponse().setBody("""{"access_token":"synthetic-new"}"""))
            mockWebServer.enqueue(MockResponse().setResponseCode(code))
            assertEquals(expected, (repository.fetchQuota(session) as Result.Failure).error::class)
            assertEquals("synthetic-new", session.credential?.accessToken)
            assertEquals(testCredential.refreshToken, session.credential?.refreshToken)
        }
    }

    @Test
    fun `fetchQuota returns success with rate limit windows`() = runTest {
        val responseJson = """
        {
            "plan_type": "pro",
            "rate_limit": {
                "primary_window": {
                    "used_percent": 45,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 18000
                },
                "secondary_window": {
                    "used_percent": 20,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 604800
                }
            },
            "credits": {
                "has_credits": true,
                "unlimited": false,
                "balance": 8.50
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.CODEX, quotaInfo.service)
        assertEquals(2, quotaInfo.windows.size)
        assertEquals("5-Hour", quotaInfo.windows[0].label)
        assertEquals(0.45, quotaInfo.windows[0].utilization, 0.001)
        assertEquals("7-Day", quotaInfo.windows[1].label)
        assertEquals(0.20, quotaInfo.windows[1].utilization, 0.001)
        assertEquals("Pro", quotaInfo.tier)
    }

    @Test
    fun `fetchQuota returns AuthError on 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
    }

    @Test
    fun `fetchQuota returns RateLimited on 429`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.RateLimited)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when no credential`() = runTest {
        credential = null

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `parseBalance handles Double`() {
        val element = kotlinx.serialization.json.JsonPrimitive(8.50)
        val balance = CodexRepositoryImpl.parseBalance(element)
        assertEquals(8.50, balance!!, 0.001)
    }

    @Test
    fun `parseBalance handles String`() {
        val element = kotlinx.serialization.json.JsonPrimitive("12.75")
        val balance = CodexRepositoryImpl.parseBalance(element)
        assertEquals(12.75, balance!!, 0.001)
    }

    @Test
    fun `parseBalance returns null for null`() {
        val balance = CodexRepositoryImpl.parseBalance(null)
        assertTrue(balance == null)
    }

    @Test
    fun `custom window label for non-standard seconds`() = runTest {
        val responseJson = """
        {
            "plan_type": "team",
            "rate_limit": {
                "primary_window": {
                    "used_percent": 30,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 3600
                }
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals("1h", quotaInfo.windows[0].label)
    }
}
