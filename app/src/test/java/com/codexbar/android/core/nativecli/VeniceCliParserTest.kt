package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class VeniceCliParserTest {
    // Shape from the bundled venice.js plugin: primary {usedPercent, resetDescription} only.
    private val success = """[{"provider":"venice","source":"api","usage":{
        "primary":{"usedPercent":37.5,"resetDescription":"DIEM 62.50 / 100.00 epoch allocation"},
        "updatedAt":"2026-10-08T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)

    private fun parse(capture: CliProcess.Result) = NativeQuotaCliParser.parse(capture, AiService.VENICE)

    @Test fun mapsBalanceWindowWithoutInventingDurationOrReset() {
        val result = parse(capture(success)) as Result.Success
        assertEquals(AiService.VENICE, result.value.service)
        val window = result.value.windows.single()
        assertEquals("Balance", window.label)
        assertEquals(0.375, window.utilization, 1e-9)
        assertEquals(UsageWindowSlot.PRIMARY, window.slot)
        assertNull(window.durationSeconds)
        assertNull(window.resetsAt)
        assertEquals(Instant.parse("2026-10-08T12:00:00Z"), result.value.fetchedAt)
    }

    @Test fun keepsExhaustedBalanceAtFullUsage() {
        val body = success.replace("37.5", "100")
        val result = parse(capture(body)) as Result.Success
        assertEquals(1.0, result.value.windows.single().utilization, 1e-9)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        for (body in listOf(success.replace("venice", "codex"), success.replace("\"api\"", "\"web\""),
            success.replace("37.5", "-1"), success.replace("37.5", "null"),
            success.replace("\"usedPercent\":37.5,", ""), "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(success, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"venice","source":"api","error":{
            "code":1,"kind":"provider","message":"Venice API error: HTTP 401 synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("401", "429"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("401", "503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
