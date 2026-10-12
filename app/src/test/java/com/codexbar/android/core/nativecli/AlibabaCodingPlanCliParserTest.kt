package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AlibabaCodingPlanCliParserTest {
    // Shape from AlibabaCodingPlanUsageSnapshot.toUsageSnapshot: fixed 5h/7d/30d windows
    // with normalized resets; loginMethod carries the plan name. Envelope provider is the
    // ProviderID rawValue "alibaba", not the "alibaba-coding-plan" CLI name.
    private val codingPlan = """[{"provider":"alibaba","source":"api","usage":{
        "primary":{"usedPercent":40,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},
        "secondary":{"usedPercent":15,"windowMinutes":10080,"resetsAt":"2026-10-15T00:00:00Z"},
        "tertiary":{"usedPercent":4,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"},
        "loginMethod":"Pro","updatedAt":"2026-10-10T12:00:00Z"}}]"""
    private val partial = """[{"provider":"alibaba","source":"api","usage":{
        "primary":{"usedPercent":80,"windowMinutes":300},
        "updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = NativeQuotaCliParser.parse(result, AiService.ALIBABA_CODING_PLAN)

    @Test fun mapsAllThreeWindowsWithRealMinutesResetsAndPlan() {
        val result = parse(capture(codingPlan)) as Result.Success
        assertEquals(AiService.ALIBABA_CODING_PLAN, result.value.service)
        assertEquals(3, result.value.windows.size)
        val primary = result.value.windows[0]
        assertEquals("5-Hour", primary.label)
        assertEquals(0.40, primary.utilization, 1e-9)
        assertEquals(UsageWindowSlot.PRIMARY, primary.slot)
        assertEquals(18000L, primary.durationSeconds)
        assertEquals(Instant.parse("2026-10-10T15:00:00Z"), primary.resetsAt)
        assertEquals("Weekly", result.value.windows[1].label)
        assertEquals(604800L, result.value.windows[1].durationSeconds)
        assertEquals("Monthly", result.value.windows[2].label)
        assertEquals(UsageWindowSlot.TERTIARY, result.value.windows[2].slot)
        assertEquals(2592000L, result.value.windows[2].durationSeconds)
        assertEquals("Pro", result.value.tier)
        assertNull(result.value.money)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), result.value.fetchedAt)
    }

    @Test fun neverInventsDurationOrResetWhenUpstreamOmitsThem() {
        val result = parse(capture(partial)) as Result.Success
        val window = result.value.windows.single()
        assertEquals(0.80, window.utilization, 1e-9)
        assertEquals(18000L, window.durationSeconds)
        assertNull(window.resetsAt)
        assertNull(result.value.tier)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        // The CLI name must never appear as the envelope provider field.
        for (body in listOf(codingPlan.replace("\"alibaba\"", "\"alibaba-coding-plan\""),
            codingPlan.replace("\"api\"", "\"web\""),
            codingPlan.replace("40", "-1"), codingPlan.replace("40", "null"),
            codingPlan.replace("\"usedPercent\":40,", ""), "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(codingPlan, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"alibaba","source":"api","error":{
            "code":1,"kind":"provider","message":"Alibaba Coding Plan API credentials are invalid or expired. synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("Alibaba Coding Plan API credentials are invalid or expired. synthetic-secret", "Alibaba Coding Plan API error: HTTP 429"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("Alibaba Coding Plan API credentials are invalid or expired. synthetic-secret", "Alibaba Coding Plan API error: HTTP 503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
