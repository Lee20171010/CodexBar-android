package com.codexbar.android.di

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.codexbar.android.core.network.ResponseSizeLimitInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkModuleTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `debug metadata logger never records credential headers or bodies`() {
        val logs = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkModule.createMetadataLoggingInterceptor { message ->
                logs += message
            })
            .build()

        val accessToken = "sentinel-access-token"
        val refreshToken = "sentinel-refresh-token"
        val clientSecret = "sentinel-client-secret"

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"$accessToken","refresh_token":"$refreshToken"}""")
        )

        val requestBody = """
            {
              "refresh_token": "$refreshToken",
              "client_secret": "$clientSecret"
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(server.url("/token"))
            .header("Authorization", "Bearer $accessToken")
            .header("Cookie", "session=$refreshToken")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
            response.body?.string()
        }

        val joinedLogs = logs.joinToString("\n")
        assertFalse(joinedLogs.contains(accessToken))
        assertFalse(joinedLogs.contains(refreshToken))
        assertFalse(joinedLogs.contains(clientSecret))
    }

    @Test
    fun `credential clients do not install HTTP logging interceptors`() {
        val tokenClients = listOf(
            NetworkModule.provideCodexTokenOkHttpClient(),
            NetworkModule.provideCodexDeviceAuthOkHttpClient(),
            NetworkModule.provideGitHubDeviceAuthOkHttpClient(),
            NetworkModule.provideProviderCredentialOkHttpClient()
        )

        tokenClients.forEach { client ->
            assertTrue(client.interceptors.none { it is HttpLoggingInterceptor })
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
        }
    }

    @Test
    fun `credential request body is not replayed to a redirect target`() {
        val redirectTarget = MockWebServer()
        redirectTarget.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(307)
                    .setHeader("Location", redirectTarget.url("/capture"))
            )
            val request = Request.Builder()
                .url(server.url("/token"))
                .post("{\"refresh_token\":\"sentinel\"}".toRequestBody("application/json".toMediaType()))
                .build()

            NetworkModule.provideCodexTokenOkHttpClient()
                .newCall(request)
                .execute()
                .use { response -> assertEquals(307, response.code) }

            assertEquals(0, redirectTarget.requestCount)
        } finally {
            redirectTarget.shutdown()
        }
    }

    @Test
    fun `all provider clients enforce a response size limit`() {
        val providerClients = listOf(
            NetworkModule.provideCodexOkHttpClient(),
            NetworkModule.provideCodexTokenOkHttpClient(),
            NetworkModule.provideCodexDeviceAuthOkHttpClient(),
            NetworkModule.provideCopilotOkHttpClient(),
            NetworkModule.provideGitHubDeviceAuthOkHttpClient(),
            NetworkModule.provideProviderCredentialOkHttpClient()
        )

        providerClients.forEach { client ->
            assertTrue(client.interceptors.any { it is ResponseSizeLimitInterceptor })
        }
    }
}
