package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import com.codexbar.android.di.NetworkModule
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class DeepSeekRepositoryImplTest {
    private fun withServer(check: suspend (MockWebServer, DeepSeekRepositoryImpl) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().followRedirects(false).build())
                .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
                .build().create(DeepSeekApiService::class.java)
            check(server, DeepSeekRepositoryImpl(api))
        }
    }
    private fun session(key: String = "synthetic-deepseek") = CredentialSession(
        AccountConnection.create(AiService.DEEPSEEK), Credential.DeepSeekCredential(key))
    private fun response(rows: String) = MockResponse().setBody("""{"is_available":true,"balance_infos":[$rows]}""")

    @Test fun `funded CNY is not hidden behind empty USD and no quota or spending is invented`() = withServer { server, repo ->
        server.enqueue(response("""{"currency":"USD","total_balance":"0"},{"currency":"CNY","total_balance":"12.34"}"""))
        val quota = (repo.fetchQuota(session()) as Result.Success).value
        assertTrue(quota.windows.isEmpty())
        assertEquals("CNY", quota.money!!.currency)
        assertEquals(12.34, quota.money.balance!!, 0.0)
        assertNull(quota.money.spent)
        assertNull(quota.money.period)
        val request = server.takeRequest()
        assertEquals("/user/balance", request.path)
        assertEquals("Bearer synthetic-deepseek", request.getHeader("Authorization"))
    }

    @Test fun `zero is reported but missing or malformed money is not zero`() = withServer { server, repo ->
        server.enqueue(response("""{"currency":"USD","total_balance":"0"}"""))
        assertEquals(0.0, ((repo.fetchQuota(session()) as Result.Success).value.money!!.balance!!), 0.0)
        for (rows in listOf("", """{"currency":"USD","total_balance":"NaN"}""",
            """{"currency":"BAD","total_balance":"1"}""", """{"currency":"USD"}""")) {
            server.enqueue(response(rows))
            assertTrue(repo.fetchQuota(session()) is Result.Failure)
        }
    }

    @Test fun `siblings send only their own key and errors never return response text`() = withServer { server, repo ->
        for ((status, key) in listOf(401 to "synthetic-a", 429 to "synthetic-b", 503 to "synthetic-c")) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("synthetic-secret"))
            val error = (repo.fetchQuota(session(key)) as Result.Failure).error
            assertEquals("Bearer $key", server.takeRequest().getHeader("Authorization"))
            assertFalse(error.toString().contains("synthetic-secret"))
            when (status) {
                401 -> assertTrue(error is AppError.AuthError)
                429 -> assertEquals(AppError.RateLimited, error)
                503 -> assertEquals(AppError.ServiceUnavailable, error)
            }
        }
    }
}
