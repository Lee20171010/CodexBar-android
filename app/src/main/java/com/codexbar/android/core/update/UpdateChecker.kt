package com.codexbar.android.core.update

import com.codexbar.android.core.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Cancellation hook: lets the caller cancel the in-flight OkHttp call. */
class CallHandle {
    private val lock = Any()
    private var call: okhttp3.Call? = null
    private var cancelled = false

    fun attach(call: okhttp3.Call) {
        synchronized(lock) {
            if (cancelled) call.cancel() else this.call = call
        }
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            call?.cancel()
        }
    }
}

/**
 * GitHub latest-release lookup for this fork only (never the upstream repo).
 * Strict input policy: stable SemVer tag, non-draft/non-prerelease, exact
 * owner/repo download URL, GitHub `sha256:<hex>` digest honored when present.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val json: Json,
    private val apiUrl: String = API_URL
) {

    data class UpdateAsset(val name: String, val url: String, val size: Long, val sha256: String?)

    data class UpdateRelease(val version: String, val asset: UpdateAsset)

    suspend fun check(handle: CallHandle = CallHandle()): Result<UpdateRelease, String> =
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            try {
                val call = client.newCall(request)
                handle.attach(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.Failure("HTTP ${response.code}")
                    }
                    val body = ByteArrayOutputStream().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        response.body?.byteStream()?.use { input ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (out.size() + read > MAX_BODY_BYTES) {
                                    return@withContext Result.Failure("Response too large")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                        out.toByteArray()
                    }
                    parse(String(body, Charsets.UTF_8))
                }
            } catch (error: Exception) {
                Result.Failure(error.javaClass.simpleName)
            }
        }

    private fun parse(body: String): Result<UpdateRelease, String> = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: return@runCatching Result.Failure("Unexpected response")
        if ((root["draft"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true ||
            (root["prerelease"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
        ) {
            return@runCatching Result.Failure("Not a stable release")
        }
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull
            ?: return@runCatching Result.Failure("Missing tag")
        // Strict stable SemVer only; the installed legacy "-beta" suffix is
        // normalized separately by isNewer().
        val match = STRICT_TAG.matchEntire(tag.trim())
            ?: return@runCatching Result.Failure("Not a stable version")
        val version = "${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3]}"
        val expectedName = "Codexbar-Android-$version.apk"
        val expectedUrl = "https://github.com/$OWNER_REPO/releases/download/$tag/$expectedName"
        val assets = root["assets"] as? JsonArray ?: JsonArray(emptyList())
        val asset = assets.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = (item["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val url = (item["browser_download_url"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            val size = (item["size"] as? JsonPrimitive)?.longOrNull ?: -1L
            // GitHub publishes "sha256:<64 hex>"; a present-but-malformed
            // digest must fail closed, never silently degrade to size-only.
            val digestField = (item["digest"] as? JsonPrimitive)?.contentOrNull
            val digest = if (digestField == null) {
                null
            } else {
                val hex = digestField.removePrefix("sha256:").lowercase(Locale.US)
                if (hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' } &&
                    digestField.startsWith("sha256:")
                ) hex else return@mapNotNull null
            }
            UpdateAsset(name, url, size, digest)
        }.firstOrNull {
            it.name == expectedName && it.url == expectedUrl && it.size in 1..MAX_APK_BYTES
        } ?: return@runCatching Result.Failure("No matching asset")
        Result.Success(UpdateRelease(version, asset))
    }.getOrElse { Result.Failure("Unexpected response") }

    companion object {
        const val OWNER_REPO = "Lee20171010/CodexBar-android"
        const val API_URL = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
        const val USER_AGENT = "CodexBar-Android"
        const val MAX_APK_BYTES = 128L * 1024 * 1024
        const val MAX_BODY_BYTES = 1_048_576
        private val STRICT_TAG = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")

        /** Leading numeric x.y.z of a version name, e.g. "0.0.15-beta" -> [0,0,15]. */
        fun versionNumbers(raw: String): List<Int>? {
            val match = Regex("^(?:v?)(\\d+)\\.(\\d+)\\.(\\d+)").find(raw.trim()) ?: return null
            return listOf(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null
            )
        }

        fun isNewer(tagName: String, currentVersionName: String): Boolean {
            val tag = versionNumbers(tagName) ?: return false
            val current = versionNumbers(currentVersionName) ?: return false
            for (i in 0..2) {
                if (tag[i] != current[i]) return tag[i] > current[i]
            }
            return false
        }
    }
}
