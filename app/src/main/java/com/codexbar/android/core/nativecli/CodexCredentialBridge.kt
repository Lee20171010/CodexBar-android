package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.Credential
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.*

/** Request-owned copy only. Android remains the sole refresh-token writer. */
object CodexCredentialBridge {
    const val HOME_PREFIX = "codexbar-cli-"

    /** Invoked under the process mutex; recover only this client's abandoned request homes. */
    fun clearAbandonedHomes(root: File) {
        root.listFiles()?.filter { it.name.startsWith(HOME_PREFIX) }?.forEach {
            check(it.deleteRecursively()) { "Unable to clear private CLI workspace" }
        }
    }

    fun needsRefresh(credential: Credential.CodexCredential, now: Instant = Instant.now()): Boolean {
        val expiry = runCatching {
            val payload = String(Base64.getUrlDecoder().decode(credential.accessToken.split('.')[1]), Charsets.UTF_8)
            Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
        }.getOrNull()
        // Margin exceeds Core's five-minute threshold and the bounded native request time.
        return if (expiry != null) !expiry.isAfter(now.plusSeconds(600))
        else credential.lastRefresh?.let { it.isAfter(now) || !it.plusSeconds(7 * 86400).isAfter(now) } ?: true
    }

    fun write(home: File, credential: Credential.CodexCredential) {
        require(!needsRefresh(credential)) { "Credential renewal required" }
        require(credential.accessToken.isNotBlank() && credential.refreshToken.isNotBlank())
        require(credential.accountId == null || (credential.accountId.length in 1..256 &&
            credential.accountId.none { it.isWhitespace() || it.isISOControl() }))
        val directory = Files.createDirectory(File(home, ".codex").toPath(),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val file = Files.createFile(directory.resolve("auth.json"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        val auth = buildJsonObject {
            putJsonObject("tokens") {
                put("access_token", credential.accessToken)
                put("refresh_token", credential.refreshToken)
                credential.accountId?.let { put("account_id", it) }
            }
            credential.lastRefresh?.let { put("last_refresh", it.toString()) }
        }
        Files.write(file, auth.toString().toByteArray(Charsets.UTF_8))
    }
}
