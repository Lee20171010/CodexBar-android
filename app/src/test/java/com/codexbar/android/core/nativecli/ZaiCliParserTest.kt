package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import com.codexbar.android.core.nativecli.NativeQuotaCliParser as ZaiCliParser

class ZaiCliParserTest {
    // Shape from zai.js via ProviderPluginSnapshotMapper: plugin extraWindows arrive as
    // extraRateWindows; primary is the shortest TOKENS/CREDIT limit, secondary the longest.
    private val codingPlan = """[{"provider":"zai","source":"api","usage":{
        "primary":{"usedPercent":37.5,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},
        "secondary":{"usedPercent":12.0,"windowMinutes":10080,"resetsAt":"2026-10-15T00:00:00Z"},
        "extraRateWindows":[{"id":"zai-mcp","title":"MCP","usageKnown":true,
          "window":{"usedPercent":5,"windowMinutes":43200}}],
        "loginMethod":"GLM Coding Pro","updatedAt":"2026-10-10T12:00:00Z"}}]"""
    private val singleLimit = """[{"provider":"zai","source":"api","usage":{
        "primary":{"usedPercent":80},
        "updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = ZaiCliParser.parse(result, AiService.ZAI)

    @Test fun mapsCodingPlanWindowsWithRealMinutesAndResets() {
        val result = parse(capture(codingPlan)) as Result.Success
        assertEquals(AiService.ZAI, result.value.service)
        assertEquals(3, result.value.windows.size)
        val primary = result.value.windows[0]
        assertEquals("5-Hour", primary.label)
        assertEquals(0.375, primary.utilization, 1e-9)
        assertEquals(UsageWindowSlot.PRIMARY, primary.slot)
        assertEquals(18000L, primary.durationSeconds)
        assertEquals(Instant.parse("2026-10-10T15:00:00Z"), primary.resetsAt)
        val secondary = result.value.windows[1]
        assertEquals("Weekly", secondary.label)
        assertEquals(0.12, secondary.utilization, 1e-9)
        assertEquals(604800L, secondary.durationSeconds)
        assertEquals("GLM Coding Pro", result.value.tier)
        val mcp = result.value.windows[2]
        assertEquals("MCP", mcp.label)
        assertEquals("extra:zai-mcp", mcp.id)
        assertTrue(mcp.supplemental)
        assertEquals(0.05, mcp.utilization, 1e-9)
        assertNull(mcp.slot)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), result.value.fetchedAt)
    }

    @Test fun neverInventsDurationOrResetWhenUpstreamOmitsThem() {
        val result = parse(capture(singleLimit)) as Result.Success
        val window = result.value.windows.single()
        assertEquals(0.80, window.utilization, 1e-9)
        assertNull(window.durationSeconds)
        assertNull(window.resetsAt)
        assertNull(result.value.tier)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        for (body in listOf(codingPlan.replace("zai", "codex"), codingPlan.replace("\"api\"", "\"web\""),
            codingPlan.replace("37.5", "-1"), codingPlan.replace("37.5", "null"),
            codingPlan.replace("\"usedPercent\":37.5,", ""), "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(codingPlan, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"zai","source":"api","error":{
            "code":1,"kind":"provider","message":"z.ai quota API error: HTTP 401 synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("z.ai quota API error: HTTP 401 synthetic-secret", "z.ai quota API error: HTTP 429"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("z.ai quota API error: HTTP 401 synthetic-secret", "z.ai quota API error: HTTP 503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
