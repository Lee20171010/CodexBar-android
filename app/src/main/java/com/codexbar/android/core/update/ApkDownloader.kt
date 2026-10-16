package com.codexbar.android.core.update

import com.codexbar.android.core.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Streams an update APK into app-private storage with size and SHA-256 checks.
 * Partial files never survive a failure or cancellation; the caller owns the
 * completed file and hands it to [ApkVerifier] before the installer.
 */
class ApkDownloader(private val client: OkHttpClient) {

    suspend fun download(
        asset: UpdateChecker.UpdateAsset,
        target: File,
        handle: CallHandle = CallHandle(),
        onProgress: (fraction: Float?) -> Unit = {}
    ): Result<File, String> = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val request = Request.Builder()
            .url(asset.url)
            .header("User-Agent", UpdateChecker.USER_AGENT)
            .build()
        val part = File(target.parentFile, target.name + ".part")
        var completed = false
        try {
            target.parentFile?.mkdirs()
            part.delete()
            val call = client.newCall(request)
            handle.attach(call)
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure("HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext Result.Failure("Empty response")
                val declared = body.contentLength()
                if (declared > UpdateChecker.MAX_APK_BYTES) {
                    return@withContext Result.Failure("File too large")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                part.outputStream().buffered().use { fileOutput ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = body.byteStream().read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        fileOutput.write(buffer, 0, read)
                        written += read
                        if (written > UpdateChecker.MAX_APK_BYTES) {
                            return@withContext Result.Failure("File too large")
                        }
                        onProgress(if (declared > 0) written.toFloat() / declared else null)
                    }
                }
                if (asset.size > 0 && written != asset.size) {
                    return@withContext Result.Failure("Size mismatch")
                }
                if (written == 0L) {
                    return@withContext Result.Failure("Empty response")
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val expected = asset.sha256?.removePrefix("sha256:")
                if (expected != null && !sha256.equals(expected, ignoreCase = true)) {
                    return@withContext Result.Failure("Digest mismatch")
                }
            }
            target.delete()
            if (!part.renameTo(target)) {
                return@withContext Result.Failure("Could not store file")
            }
            completed = true
            Result.Success(target)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.Failure(error.javaClass.simpleName)
        } finally {
            if (!completed) part.delete()
        }
    }
}
