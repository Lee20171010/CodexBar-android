package com.codexbar.android.core.update

import com.codexbar.android.core.domain.model.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer()
    private lateinit var checker: UpdateChecker

    @Before fun setup() {
        server.start()
        checker = UpdateChecker(
            OkHttpClient(),
            Json { ignoreUnknownKeys = true },
            server.url("/repos/Lee20171010/CodexBar-android/releases/latest").toString()
        )
    }

    @After fun teardown() { server.shutdown() }

    private fun reply(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun releaseJson(
        tag: String = "v0.0.16",
        name: String = "Codexbar-Android-0.0.16.apk",
        url: String = "https://github.com/Lee20171010/CodexBar-android/releases/download/v0.0.16/Codexbar-Android-0.0.16.apk",
        size: Long = 36_000_000,
        digest: String? = "sha256:" + "a".repeat(64),
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[
          {"name":"notes.txt","browser_download_url":"https://example.invalid/n.txt","size":10},
          {"name":"$name","browser_download_url":"$url","size":$size,
           ${if (digest != null) "\"digest\":\"$digest\"," else ""}"updated_at":"2026-10-16T00:00:00Z"}
        ]}
    """.trimIndent()

    @Test fun `version comparison is numeric and normalizes installed legacy suffix`() {
        assertTrue(UpdateChecker.isNewer("0.0.16", "0.0.15-beta"))
        assertTrue(UpdateChecker.isNewer("v1.2.10", "1.2.9"))
        assertFalse(UpdateChecker.isNewer("0.0.15", "0.0.15-beta"))
        assertFalse(UpdateChecker.isNewer("0.0.14", "0.0.15-beta"))
        assertFalse(UpdateChecker.isNewer("not-a-version", "0.0.15-beta"))
        assertFalse(UpdateChecker.isNewer("0.0.16", "garbage"))
    }

    @Test fun `redirect policy pins https default port no userinfo github hosts`() {
        assertTrue(UpdateRedirectPolicy.isAllowed("https://github.com/a/b".toHttpUrl()))
        assertTrue(
            UpdateRedirectPolicy.isAllowed(
                "https://release-assets.githubusercontent.com/x".toHttpUrl()
            )
        )
        assertFalse(UpdateRedirectPolicy.isAllowed("http://github.com/a/b".toHttpUrl()))
        assertFalse(UpdateRedirectPolicy.isAllowed("https://evil.example.com/a.apk".toHttpUrl()))
        assertFalse(UpdateRedirectPolicy.isAllowed("https://github.com:8443/a/b".toHttpUrl()))
        assertFalse(UpdateRedirectPolicy.isAllowed("https://user@github.com/a/b".toHttpUrl()))
    }

    @Test fun `client with policy blocks plain http redirect target`() {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .addNetworkInterceptor(UpdateRedirectPolicy())
            .build()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://evil.example.com/a.apk"))
        val result = runCatching {
            client.newCall(okhttp3.Request.Builder().url(server.url("/start")).build()).execute()
        }
        assertTrue(result.isFailure)
    }

    @Test fun `latest release parses version github sha256 digest and asset`() = runBlocking {
        reply(releaseJson())
        val result = checker.check()
        assertTrue(result.isSuccess.toString(), result is Result.Success)
        val release = (result as Result.Success).value
        assertEquals("0.0.16", release.version)
        assertEquals("Codexbar-Android-0.0.16.apk", release.asset.name)
        assertEquals(36_000_000L, release.asset.size)
        assertEquals("a".repeat(64), release.asset.sha256)
    }

    @Test fun `prerelease draft and non strict tags fail closed`() = runBlocking {
        reply(releaseJson(prerelease = true))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(draft = true))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(tag = "v0.0.16-beta-native"))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(tag = "v0.0.16+build9"))
        assertTrue(checker.check() is Result.Failure)
    }

    @Test fun `asset must match exact owner repo tag path and sane size`() = runBlocking {
        reply(releaseJson(url = "https://github.com/evil/CodexBar-android/releases/download/v0.0.16/Codexbar-Android-0.0.16.apk"))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(url = "https://github.com/Lee20171010/CodexBar-android/releases/download/v9.9.9/Codexbar-Android-0.0.16.apk"))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(name = "Codexbar-Android-0.0.99.apk"))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(size = 0))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(size = UpdateChecker.MAX_APK_BYTES + 1))
        assertTrue(checker.check() is Result.Failure)
    }

    @Test fun `present but malformed digest fails closed not silently dropped`() = runBlocking {
        reply(releaseJson(digest = "zz"))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(digest = "md5:" + "a".repeat(32)))
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(digest = "a".repeat(64))) // missing sha256: prefix
        assertTrue(checker.check() is Result.Failure)

        reply(releaseJson(digest = null)) // absent digest stays allowed (size+signature checks)
        assertTrue(checker.check() is Result.Success)
    }

    @Test fun `oversized http errors and garbage bodies fail closed`() = runBlocking {
        reply("x".repeat(UpdateChecker.MAX_BODY_BYTES + 1))
        assertEquals(
            "Response too large",
            (checker.check() as Result.Failure).error
        )

        reply("nope", code = 404)
        assertEquals("HTTP 404", (checker.check() as Result.Failure).error)

        reply("<html>not json")
        assertTrue(checker.check() is Result.Failure)
    }

    @Test fun `call handle cancels attached and future calls`() {
        var cancelled = false
        val call = object : okhttp3.Call {
            override fun request() = okhttp3.Request.Builder().url("https://github.com/").build()
            override fun execute() = throw IllegalStateException()
            override fun enqueue(responseCallback: okhttp3.Callback) = throw IllegalStateException()
            override fun cancel() { cancelled = true }
            override fun clone() = this
            override fun isExecuted() = false
            override fun isCanceled() = cancelled
            override fun timeout() = okio.Timeout.NONE
        }
        val late = CallHandle()
        late.cancel()
        late.attach(call)
        assertTrue(cancelled)

        val early = CallHandle()
        cancelled = false
        early.attach(call)
        early.cancel()
        assertTrue(cancelled)
    }
}
