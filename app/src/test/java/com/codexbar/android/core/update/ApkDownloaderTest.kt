package com.codexbar.android.core.update

import com.codexbar.android.core.domain.model.Result
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val temp = TemporaryFolder()
    private val server = MockWebServer()
    private val downloader = ApkDownloader(OkHttpClient())
    private val payload = ByteArray(4096) { (it % 251).toByte() }

    @Before fun setup() { server.start() }
    @After fun teardown() { server.shutdown() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun asset(size: Long = payload.size.toLong(), digest: String? = sha256(payload)) =
        UpdateChecker.UpdateAsset(
            "Codexbar-Android-0.0.16.apk",
            server.url("/download.apk").toString(),
            size,
            digest
        )

    @Test fun `matching size and digest stores file and removes part`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val target = File(temp.root, "update.apk")
        var lastFraction: Float? = null
        val result = downloader.download(asset(), target) { lastFraction = it }
        assertTrue(result.isSuccess)
        assertArrayEquals(payload, target.readBytes())
        assertEquals(1f, lastFraction)
        assertFalse(File(temp.root, "update.apk.part").exists())
    }

    @Test fun `digest mismatch deletes partial and fails`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val target = File(temp.root, "update.apk")
        val result = downloader.download(asset(digest = "b".repeat(64)), target)
        assertEquals("Digest mismatch", (result as Result.Failure).error)
        assertFalse(target.exists())
        assertFalse(File(temp.root, "update.apk.part").exists())
    }

    @Test fun `size mismatch fails closed`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val result = downloader.download(asset(size = payload.size + 1L), File(temp.root, "u.apk"))
        assertEquals("Size mismatch", (result as Result.Failure).error)
    }

    @Test fun `http errors and empty bodies fail without leaving files`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val target = File(temp.root, "u.apk")
        assertEquals("HTTP 500", (downloader.download(asset(size = -1), target) as Result.Failure).error)
        assertFalse(target.exists())

        server.enqueue(MockResponse().setBody(""))
        assertEquals(
            "Empty response",
            (downloader.download(asset(size = -1), target) as Result.Failure).error
        )
        assertFalse(File(temp.root, "u.apk.part").exists())
    }

    @Test fun `truncated download fails and leaves no partial`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(payload))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val target = File(temp.root, "u.apk")
        val result = downloader.download(asset(size = -1), target)
        assertTrue(result.isFailure.toString(), result is Result.Failure)
        assertFalse(target.exists())
        assertFalse(File(temp.root, "u.apk.part").exists())
    }

    @Test fun `verifier requires same package newer version and matching signer`() {
        val cert = byteArrayOf(1, 2, 3)
        assertNull(
            ApkVerifier.verifyArchive("com.codexbar.android", 17L, listOf(cert), "com.codexbar.android", 16L, listOf(cert))
        )
        assertEquals("Package mismatch", ApkVerifier.verifyArchive("com.other", 17L, listOf(cert), "com.codexbar.android", 16L, listOf(cert)))
        assertEquals("Package mismatch", ApkVerifier.verifyArchive(null, 17L, listOf(cert), "com.codexbar.android", 16L, listOf(cert)))
        assertEquals("Not newer", ApkVerifier.verifyArchive("com.codexbar.android", 16L, listOf(cert), "com.codexbar.android", 16L, listOf(cert)))
        assertEquals("No signing info", ApkVerifier.verifyArchive("com.codexbar.android", 17L, emptyList(), "com.codexbar.android", 16L, listOf(cert)))
        assertEquals("Signature mismatch", ApkVerifier.verifyArchive("com.codexbar.android", 17L, listOf(byteArrayOf(9)), "com.codexbar.android", 16L, listOf(cert)))
        // Multi-signer APK with only partial overlap must not pass.
        assertEquals("Signature mismatch", ApkVerifier.verifyArchive("com.codexbar.android", 17L, listOf(cert, byteArrayOf(9)), "com.codexbar.android", 16L, listOf(cert)))
    }
}
