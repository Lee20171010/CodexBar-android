package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class FactoryCliParserTest {
    // Legacy shape: FactoryStatusProbe.toUsageSnapshot primary=Standard/secondary=Premium,
    // windowMinutes nil, resetsAt=periodEnd; providerCost.used carries the extra-usage balance.
    private val legacy = """[{"provider":"factory","source":"api","usage":{
        "primary":{"usedPercent":62.5,"resetsAt":"2026-10-10T18:00:00Z"},
        "secondary":{"usedPercent":30.0,"resetsAt":"2026-10-12T18:00:00Z"},
        "providerCost":{"used":12.5,"limit":0,"currencyCode":"USD",
          "period":"Extra usage balance","updatedAt":"2026-10-10T11:00:00Z"},
        "loginMethod":"Factory Pro - Pro","updatedAt":"2026-10-10T12:00:00Z"}}]"""
    // tokenRateLimits shape: 5h/7-day windows with real minutes, Monthly without,
    // plus factory-core-* NamedRateWindow extras.
    private val tokenRateLimits = """[{"provider":"factory","source":"api","usage":{
        "primary":{"usedPercent":40,"windowMinutes":300,"resetsAt":"2026-10-10T15:00:00Z"},
        "secondary":{"usedPercent":15,"windowMinutes":10080,"resetsAt":"2026-10-15T00:00:00Z"},
        "tertiary":{"usedPercent":3},
        "extraRateWindows":[{"id":"factory-core-5h","title":"Core 5h","usageKnown":true,
          "window":{"usedPercent":10,"windowMinutes":300}},
          {"id":"factory-core-monthly","title":"Core Monthly","usageKnown":false,
          "window":{"usedPercent":1}}],
        "loginMethod":"Factory Max","updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = NativeQuotaCliParser.parse(result, AiService.FACTORY)

    @Test fun mapsLegacyWindowsWithTierAndExtraUsageBalance() {
        val result = parse(capture(legacy)) as Result.Success
        assertEquals(AiService.FACTORY, result.value.service)
        val primary = result.value.windows[0]
        assertEquals("Standard", primary.label)
        assertEquals(0.625, primary.utilization, 1e-9)
        assertEquals(UsageWindowSlot.PRIMARY, primary.slot)
        assertNull(primary.durationSeconds)
        assertEquals(Instant.parse("2026-10-10T18:00:00Z"), primary.resetsAt)
        assertEquals("Premium", result.value.windows[1].label)
        assertEquals("Factory Pro - Pro", result.value.tier)
        val money = result.value.money!!
        assertEquals(12.5, money.balance!!, 1e-9)
        assertNull(money.spent)
        assertEquals("USD", money.currency)
        assertEquals("Extra usage balance", money.period)
        assertEquals(Instant.parse("2026-10-10T11:00:00Z"), money.fetchedAt)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), result.value.fetchedAt)
    }

    @Test fun mapsTokenRateLimitWindowsWithRealMinutesAndCoreExtras() {
        val result = parse(capture(tokenRateLimits)) as Result.Success
        assertEquals(4, result.value.windows.size)
        assertEquals("5-Hour", result.value.windows[0].label)
        assertEquals(18000L, result.value.windows[0].durationSeconds)
        assertEquals("7-Day", result.value.windows[1].label)
        assertEquals(604800L, result.value.windows[1].durationSeconds)
        assertEquals("Monthly", result.value.windows[2].label)
        assertEquals(UsageWindowSlot.TERTIARY, result.value.windows[2].slot)
        assertNull(result.value.windows[2].durationSeconds)
        val core = result.value.windows[3]
        assertEquals("Core 5h", core.label)
        assertEquals("extra:factory-core-5h", core.id)
        assertTrue(core.supplemental)
        assertEquals(18000L, core.durationSeconds)
        assertEquals("Factory Max", result.value.tier)
        assertNull(result.value.money)
    }

    @Test fun rejectsWrongProviderSourceExitAndMalformedMeasurements() {
        for (body in listOf(legacy.replace("factory", "codex"), legacy.replace("\"api\"", "\"web\""),
            legacy.replace("62.5", "-1"), legacy.replace("62.5", "null"),
            legacy.replace("\"usedPercent\":62.5,", ""), legacy.replace("\"used\":12.5", "\"used\":-2"),
            "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(legacy, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"factory","source":"api","error":{
            "code":1,"kind":"provider","message":"Droid API authentication failed (401/403). synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val rate = parse(capture(body.replace("Droid API authentication failed (401/403). synthetic-secret", "Factory API error: HTTP 429"), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("Droid API authentication failed (401/403). synthetic-secret", "Factory API error: HTTP 503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
