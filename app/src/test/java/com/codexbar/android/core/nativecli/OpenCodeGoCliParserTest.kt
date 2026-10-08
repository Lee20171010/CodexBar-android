package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import com.codexbar.android.core.nativecli.NativeQuotaCliParser as OpenCodeGoCliParser

class OpenCodeGoCliParserTest {
    private val success = """[{"provider":"opencodego","source":"api","usage":{
        "primary":{"usedPercent":25,"resetsAt":"2026-10-08T01:00:00Z"},
        "secondary":{"usedPercent":60},"tertiary":{"usedPercent":110},
        "updatedAt":"2026-10-07T23:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)

    @Test fun mapsThreeWindowsWithoutLosingOverQuotaUsage() {
        val result = OpenCodeGoCliParser.parse(capture(success)) as Result.Success
        assertEquals(AiService.OPENCODE_GO, result.value.service)
        assertEquals(listOf("5-Hour", "Weekly", "Monthly"), result.value.windows.map { it.label })
        assertEquals(listOf(0.25, 0.60, 1.10), result.value.windows.map { it.utilization })
        assertEquals(Instant.parse("2026-10-08T01:00:00Z"), result.value.windows[0].resetsAt)
        assertNull(result.value.windows[1].resetsAt)
        assertEquals(Instant.parse("2026-10-07T23:00:00Z"), result.value.fetchedAt)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        for (body in listOf(success.replace("opencodego", "codex"), success.replace("\"api\"", "\"local\""),
            success.replace("25", "-1"), success.replace("25", "null"), "[]", "not JSON")) {
            assertTrue(OpenCodeGoCliParser.parse(capture(body)) is Result.Failure)
        }
        assertTrue(OpenCodeGoCliParser.parse(capture(success, 139)) is Result.Failure)
    }

    @Test fun doesNotInventMissingOrPlaceholderWindows() {
        val body = """[{"provider":"opencodego","source":"api","usage":{
            "primary":{"usedPercent":5},"secondary":null,
            "tertiary":{"usedPercent":0,"isSyntheticPlaceholder":true},
            "updatedAt":"2026-10-07T23:00:00Z"}}]"""
        val result = OpenCodeGoCliParser.parse(capture(body)) as Result.Success
        assertEquals(1, result.value.windows.size)
        assertTrue(OpenCodeGoCliParser.parse(capture(body.replace("\"usedPercent\":5", "\"usedPercent\":5,\"isSyntheticPlaceholder\":true"))) is Result.Failure)
    }

    @Test fun rejectsTruncatedAndTimedOutSuccessOutput() {
        assertTrue(OpenCodeGoCliParser.parse(CliProcess.Result(0, true, 1, success, "", false, false)) is Result.Failure)
        assertTrue(OpenCodeGoCliParser.parse(CliProcess.Result(0, false, 1, success, "", true, false)) is Result.Failure)
    }

    @Test fun mapsAuthenticationFailureWithoutExposingSecrets() {
        val body = """[{"provider":"opencodego","source":"api","error":{
            "code":1,"kind":"provider","message":"OpenCode Go credentials are invalid or expired. synthetic-secret"}}]"""
        val result = OpenCodeGoCliParser.parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val unknown = OpenCodeGoCliParser.parse(capture(body.replace("credentials are invalid or expired", "custom server error"), 1)) as Result.Failure
        assertFalse(unknown.error.toString().contains("synthetic-secret"))
    }
}
