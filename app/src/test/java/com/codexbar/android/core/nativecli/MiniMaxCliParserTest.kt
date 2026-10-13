package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MiniMaxCliParserTest {
    // Shape from MiniMaxUsageSnapshot.toUsageSnapshot: per-model services fill primary/
    // secondary/tertiary; loginMethod is flattened into usage by UsageSnapshot.encode.
    private val quota = """[{"provider":"minimax","source":"api","usage":{
        "primary":{"usedPercent":37.5,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},
        "secondary":{"usedPercent":62.0,"windowMinutes":10080,"resetsAt":"2026-10-12T00:00:00Z"},
        "tertiary":{"usedPercent":5.0,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"},
        "loginMethod":"MiniMax M2 Pro","dataConfidence":"exact",
        "updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = NativeQuotaCliParser.parse(result, AiService.MINIMAX)

    @Test fun mapsThreeSlotsWithDurationDerivedLabels() {
        val info = (parse(capture(quota)) as Result.Success).value
        assertEquals(AiService.MINIMAX, info.service)
        assertEquals("MiniMax M2 Pro", info.tier)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), info.fetchedAt)
        val windows = info.windows
        assertEquals(3, windows.size)
        assertEquals("5-Hour", windows[0].label)
        assertEquals(UsageWindowSlot.PRIMARY, windows[0].slot)
        assertEquals(0.375, windows[0].utilization, 1e-9)
        assertEquals(18_000L, windows[0].durationSeconds)
        assertEquals(Instant.parse("2026-10-10T15:00:00Z"), windows[0].resetsAt)
        assertEquals("7-Day", windows[1].label)
        assertEquals(UsageWindowSlot.SECONDARY, windows[1].slot)
        assertEquals(604_800L, windows[1].durationSeconds)
        assertEquals("Monthly", windows[2].label)
        assertEquals(UsageWindowSlot.TERTIARY, windows[2].slot)
        assertEquals(2_592_000L, windows[2].durationSeconds)
        assertNull(info.money)
    }

    @Test fun unknownDurationFallsBackToNeutralSlotLabel() {
        val body = quota.replace(""""windowMinutes":10080,""", "")
        val info = (parse(capture(body)) as Result.Success).value
        assertEquals("Secondary", info.windows[1].label)
        assertNull(info.windows[1].durationSeconds)
        val odd = quota.replace("\"windowMinutes\":10080", "\"windowMinutes\":480")
        assertEquals("480 min", (parse(capture(odd)) as Result.Success).value.windows[1].label)
    }

    @Test fun emptyWindowsAndEnvelopeMismatchFailClosed() {
        val noWindows = quota
            .replace(""""primary":{"usedPercent":37.5,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},""", """"primary":null,""")
            .replace(""""secondary":{"usedPercent":62.0,"windowMinutes":10080,"resetsAt":"2026-10-12T00:00:00Z"},""", """"secondary":null,""")
            .replace(""""tertiary":{"usedPercent":5.0,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"},""", """"tertiary":null,""")
        for ((i, body) in listOf(
            noWindows,
            quota.replace("\"usedPercent\":37.5", "\"usedPercent\":-1"),
            quota.replace("\"usedPercent\":37.5", "\"usedPercent\":null"),
            quota.replace("minimax", "codex"),
            quota.replace("\"api\"", "\"web\""),
            "[]", "not JSON"
        ).withIndex()) {
            assertTrue("case $i must fail closed", parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(quota, 139)) is Result.Failure)
    }

    @Test fun otherProvidersKeepRawMinutesLabelForMonthlyDuration() {
        // The MiniMax-scoped 43200→Monthly mapping must not leak into existing providers.
        val codex = """[{"provider":"codex","source":"oauth","usage":{
            "primary":{"usedPercent":10.0,"windowMinutes":43200},
            "updatedAt":"2026-10-10T12:00:00Z"}}]"""
        val info = (NativeQuotaCliParser.parse(capture(codex), AiService.CODEX) as Result.Success).value
        assertEquals("43200 min", info.windows[0].label)
    }

    @Test fun mapsAuthenticationRateLimitAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"minimax","source":"api","error":{
            "code":1,"kind":"provider","message":"MiniMax credentials are invalid or expired. synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("MiniMax credentials are invalid or expired. synthetic-secret", "MiniMax API error: HTTP 429 Too Many Requests"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("MiniMax credentials are invalid or expired. synthetic-secret", "MiniMax API error: HTTP 503 Service Unavailable"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
