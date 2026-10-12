package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import com.codexbar.android.core.nativecli.NativeQuotaCliParser as AmpCliParser

class AmpCliParserTest {
    // Shape from AmpUsageSnapshot.toUsageSnapshot: subscription primary (otherUsedPercent,
    // period minutes, resetsAt) plus optional orb secondary.
    private val subscription = """[{"provider":"amp","source":"api","usage":{
        "primary":{"usedPercent":62.5,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"},
        "secondary":{"usedPercent":30.0,"windowMinutes":43200,"resetsAt":"2026-11-01T00:00:00Z"},
        "extraRateWindows":[{"id":"amp-free","title":"Amp Free","usageKnown":true,
          "window":{"usedPercent":12.5,"windowMinutes":1440,"resetsAt":"2026-10-10T20:00:00Z"}}],
        "loginMethod":"Amp Pro","updatedAt":"2026-10-10T12:00:00Z"}}]"""
    private val freeOnly = """[{"provider":"amp","source":"api","usage":{
        "primary":{"usedPercent":40,"windowMinutes":1440,"resetsAt":"2026-10-10T20:00:00Z"},
        "loginMethod":"Amp Free","updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = AmpCliParser.parse(result, AiService.AMP)

    @Test fun mapsSubscriptionWindowsWithMinutesAndResets() {
        val result = parse(capture(subscription)) as Result.Success
        assertEquals(AiService.AMP, result.value.service)
        assertEquals(3, result.value.windows.size)
        val primary = result.value.windows[0]
        assertEquals("Amp", primary.label)
        assertEquals(0.625, primary.utilization, 1e-9)
        assertEquals(UsageWindowSlot.PRIMARY, primary.slot)
        assertEquals(2592000L, primary.durationSeconds)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), primary.resetsAt)
        val secondary = result.value.windows[1]
        assertEquals("Orb", secondary.label)
        assertEquals(0.30, secondary.utilization, 1e-9)
        assertEquals(UsageWindowSlot.SECONDARY, secondary.slot)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), result.value.fetchedAt)
        // loginMethod is the upstream subscription plan; kept as tier.
        assertEquals("Amp Pro", result.value.tier)
        // amp-free extra survives as a supplemental named window (upstream NamedRateWindow).
        val free = result.value.windows[2]
        assertEquals("Amp Free", free.label)
        assertEquals("extra:amp-free", free.id)
        assertTrue(free.supplemental)
        assertEquals(0.125, free.utilization, 1e-9)
        assertEquals(86400L, free.durationSeconds)
        assertNull(free.slot)
    }

    @Test fun mapsFreeOnlySnapshotToSingleWindow() {
        val result = parse(capture(freeOnly)) as Result.Success
        val window = result.value.windows.single()
        assertEquals("Amp", window.label)
        assertEquals(0.40, window.utilization, 1e-9)
        assertEquals(86400L, window.durationSeconds)
        assertEquals("Amp Free", result.value.tier)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        for (body in listOf(subscription.replace("amp", "codex"), subscription.replace("\"api\"", "\"cli\""),
            subscription.replace("62.5", "-1"), subscription.replace("62.5", "null"),
            subscription.replace("\"usedPercent\":62.5,", ""), "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(subscription, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"amp","source":"api","error":{
            "code":1,"kind":"provider","message":"Amp access token is invalid or expired. synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("Amp access token is invalid or expired. synthetic-secret", "Amp request failed: HTTP 429"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("Amp access token is invalid or expired. synthetic-secret", "Amp request failed: HTTP 503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
