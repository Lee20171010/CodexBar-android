package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class KimiCliParserTest {
    // Shape from KimiUsageSnapshot.toUsageSnapshot: weekly pool is primary (limit_7d),
    // session pool secondary (limit_5h), monthly total pool arrives as an extra window.
    private val quota = """[{"provider":"kimi","source":"api","usage":{
        "primary":{"usedPercent":42.5,"windowMinutes":10080,"resetsAt":"2026-10-12T00:00:00Z"},
        "secondary":{"usedPercent":25.0,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},
        "extraRateWindows":[{"id":"kimi-monthly","title":"Total usage","usageKnown":true,
          "window":{"usedPercent":10.0,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"}}],
        "loginMethod":"Allegro","dataConfidence":"exact",
        "updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = NativeQuotaCliParser.parse(result, AiService.KIMI)

    @Test fun mapsWeeklyPrimarySessionSecondaryAndMonthlyExtra() {
        val result = parse(capture(quota)) as Result.Success
        val info = result.value
        assertEquals(AiService.KIMI, info.service)
        assertEquals("Allegro", info.tier)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), info.fetchedAt)
        val windows = info.windows
        assertEquals(3, windows.size)
        val weekly = windows[0]
        assertEquals("7-Day", weekly.label)
        assertEquals(UsageWindowSlot.PRIMARY, weekly.slot)
        assertEquals(0.425, weekly.utilization, 1e-9)
        assertEquals(604_800L, weekly.durationSeconds)
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), weekly.resetsAt)
        val session = windows[1]
        assertEquals("5-Hour", session.label)
        assertEquals(UsageWindowSlot.SECONDARY, session.slot)
        assertEquals(18_000L, session.durationSeconds)
        val monthly = windows[2]
        assertEquals("Total usage", monthly.label)
        assertTrue(monthly.supplemental)
        assertEquals("extra:kimi-monthly", monthly.id)
        assertEquals(2_592_000L, monthly.durationSeconds)
        assertNull(monthly.slot)
    }

    @Test fun unknownDurationKeepsBaseLabelAndNullDuration() {
        val body = quota.replace(""""windowMinutes":10080,""", "")
        val result = parse(capture(body)) as Result.Success
        assertEquals("Weekly", result.value.windows[0].label)
        assertNull(result.value.windows[0].durationSeconds)
        val odd = quota.replace("\"windowMinutes\":10080", "\"windowMinutes\":480")
        assertEquals("480 min", (parse(capture(odd)) as Result.Success).value.windows[0].label)
    }

    @Test fun malformedPercentAndEmptyWindowsFailClosed() {
        val noPools = quota
            .replace(""""secondary":{"usedPercent":25.0,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},""", """"secondary":null,""")
            .replace(""""primary":{"usedPercent":42.5,"windowMinutes":10080,"resetsAt":"2026-10-12T00:00:00Z"},""", """"primary":null,""")
            .replace(""""extraRateWindows":[{"id":"kimi-monthly","title":"Total usage","usageKnown":true,
          "window":{"usedPercent":10.0,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"}}],""", """"extraRateWindows":[],""")
        for ((i, body) in listOf(
            quota.replace("\"usedPercent\":42.5", "\"usedPercent\":-1"),
            quota.replace("\"usedPercent\":42.5", "\"usedPercent\":\"NaN\""),
            quota.replace("\"usedPercent\":42.5", "\"usedPercent\":null"),
            noPools,
            quota.replace("kimi", "codex"),
            quota.replace("\"api\"", "\"web\""),
            "[]", "not JSON"
        ).withIndex()) {
            assertTrue("case $i must fail closed", parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(quota, 139)) is Result.Failure)
    }

    @Test fun monthlyExtraAloneStillYieldsUsableQuota() {
        // Generic parser behavior: a valid supplemental window keeps the response usable even
        // when both principal pools are absent; only a fully empty window list fails closed.
        val body = quota
            .replace(""""secondary":{"usedPercent":25.0,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},""", """"secondary":null,""")
            .replace(""""primary":{"usedPercent":42.5,"windowMinutes":10080,"resetsAt":"2026-10-12T00:00:00Z"},""", """"primary":null,""")
        val result = parse(capture(body)) as Result.Success
        assertEquals(1, result.value.windows.size)
        assertEquals("Total usage", result.value.windows.single().label)
    }

    @Test fun mapsAuthenticationRateLimitAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"kimi","source":"api","error":{
            "code":1,"kind":"provider","message":"Kimi Code API key is invalid or expired. Please refresh your API key. synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("Kimi Code API key is invalid or expired. Please refresh your API key. synthetic-secret", "Kimi API error: HTTP 429 Too Many Requests"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("Kimi Code API key is invalid or expired. Please refresh your API key. synthetic-secret", "Kimi API error: HTTP 503 Service Unavailable"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
