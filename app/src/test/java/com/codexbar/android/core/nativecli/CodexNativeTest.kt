package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CodexNativeTest {
    @Test fun `freshness is real JWT expiry or recorded exchange time and auth copy is private`() {
        val now = Instant.now()
        fun jwt(expiry: Instant) = "header." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":${expiry.epochSecond}}""".toByteArray()) + ".signature"
        val credential = Credential.CodexCredential(jwt(now.plusSeconds(3600)), "synthetic-refresh", "account-a")
        assertFalse(CodexCredentialBridge.needsRefresh(credential, now))
        assertTrue(CodexCredentialBridge.needsRefresh(credential.copy(accessToken = jwt(now.plusSeconds(599))), now))
        assertTrue(CodexCredentialBridge.needsRefresh(credential.copy(accessToken = "opaque"), now))
        assertFalse(CodexCredentialBridge.needsRefresh(credential.copy(accessToken = "opaque", lastRefresh = now), now))
        assertTrue(CodexCredentialBridge.needsRefresh(credential.copy(accessToken = "opaque", lastRefresh = now.plusSeconds(1)), now))
        val home = Files.createTempDirectory("codex-bridge-test-").toFile()
        try {
            CodexCredentialBridge.write(home, credential)
            val directory = home.resolve(".codex").toPath()
            val file = directory.resolve("auth.json")
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory))
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
            val auth = Json.parseToJsonElement(file.toFile().readText()).jsonObject
            assertFalse(auth.containsKey("last_refresh"))
            assertEquals("account-a", auth.getValue("tokens").jsonObject.getValue("account_id").jsonPrimitive.content)
            val abandoned = home.resolve(CodexCredentialBridge.HOME_PREFIX + "orphan").apply { mkdir() }
            abandoned.resolve("auth.json").writeText("synthetic-private-data")
            CodexCredentialBridge.clearAbandonedHomes(home)
            assertFalse(abandoned.exists())
            assertTrue(file.toFile().exists()) // Unrelated homes are not owned by cleanup.
        } finally { home.deleteRecursively() }
    }

    private val success = """[{"provider":"codex","source":"oauth","usage":{
        "secondary":{"usedPercent":57,"windowMinutes":10080,"resetsAt":"2026-10-20T00:00:00Z"},
        "extraRateWindows":[{"id":"model-a","title":"Model A","window":{"usedPercent":110}},
            {"id":"unknown","title":"Unknown","usageKnown":false,"window":{"usedPercent":100}}],
        "loginMethod":"Pro","updatedAt":"2026-10-13T00:00:00Z"}}]"""
    private fun parse(text: String, exit: Int = 0) = NativeQuotaCliParser.parse(
        CliProcess.Result(exit, false, 1, text, "", false, false), AiService.CODEX)

    @Test fun `weekly-only Codex and supplemental pools keep their own identities and clocks`() {
        val quota = (parse(success) as Result.Success).value
        assertEquals(listOf("secondary", "extra:model-a"), quota.windows.map { it.id })
        assertEquals("7-Day", quota.windows.first().label)
        assertEquals(604800L, quota.windows.first().durationSeconds)
        assertFalse(quota.windows.first().supplemental)
        assertTrue(quota.windows.last().supplemental)
        assertEquals(1.1, quota.windows.last().utilization, 0.0001)
        assertNull(quota.windows.last().resetsAt)
        assertEquals("Pro", quota.tier)
        assertTrue(parse(success.replace("oauth", "cli")) is Result.Failure)
        assertTrue(parse(success.replace("codex", "copilot")) is Result.Failure)
        assertTrue(parse(success, 139) is Result.Failure)
    }

    @Test fun `native failures retain auth versus outage without leaking messages`() {
        for ((message, type) in listOf("Codex OAuth token expired or invalid" to AppError.AuthError::class.java,
            "Codex API error 429" to AppError.RateLimited::class.java,
            "Codex API error 502" to AppError.ServiceUnavailable::class.java)) {
            val result = parse("""[{"provider":"codex","source":"oauth","error":{"message":"$message synthetic-secret"}}]""", 1) as Result.Failure
            assertTrue(type.isInstance(result.error))
            assertFalse(result.error.toString().contains("synthetic-secret"))
        }
    }
}
