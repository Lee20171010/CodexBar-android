package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageDataConfidence
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class HyperCliParserTest {
    // Shape from hyper.js via ProviderPluginSnapshotMapper: balance-only provider, no percentage
    // windows. The raw number arrives as usageValue on the "Hypercredits"/"Balance" detail row;
    // the formatted "value" string must never be parsed.
    private val balance = """[{"provider":"hyper","source":"api","usage":{
        "details":[{"title":"Hypercredits","rows":[
          {"label":"Balance","value":"12.34 HC","usageValue":12.34}]}],
        "loginMethod":"API key","dataConfidence":"exact",
        "updatedAt":"2026-10-10T12:00:00Z"}}]"""

    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 10, text, "", false, false)
    private fun parse(result: CliProcess.Result) = NativeQuotaCliParser.parse(result, AiService.HYPER)

    @Test fun mapsBalanceDetailRowToCreditUnitMoneyWithoutWindows() {
        val result = parse(capture(balance)) as Result.Success
        assertEquals(AiService.HYPER, result.value.service)
        assertTrue(result.value.windows.isEmpty())
        val money = result.value.money!!
        assertEquals(12.34, money.balance!!, 1e-9)
        assertNull(money.spent)
        assertEquals("HC", money.currency)
        assertNull(money.period)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), money.fetchedAt)
        assertEquals("API key", result.value.tier)
        assertEquals(UsageDataConfidence.EXACT, result.value.dataConfidence)
    }

    @Test fun zeroBalanceIsKeptAndDistinctFromMissing() {
        val result = parse(capture(balance.replace("12.34}", "0}"))) as Result.Success
        assertEquals(0.0, result.value.money!!.balance!!, 1e-9)
        assertTrue(parse(capture(balance.replace(",\"usageValue\":12.34", ""))) is Result.Failure)
    }

    @Test fun rejectsNonFiniteNegativeOrMislabelledBalances() {
        for (body in listOf(balance.replace("12.34}", "null}"), balance.replace("12.34}", "\"NaN\"}"),
            balance.replace("12.34}", "-1}"), balance.replace("Hypercredits", "Other"),
            balance.replace("\"label\":\"Balance\"", "\"label\":\"Credits\""),
            balance.replace("\"details\":[", "\"details\":{"),
            balance.replace("\"rows\":", "\"unrecognizedRows\":"))) {
            assertNotEquals("Malformed fixture must differ from valid payload", balance, body)
            assertTrue(parse(capture(body)) is Result.Failure)
        }
    }

    @Test fun rejectsWrongProviderSourceAndExit() {
        for (body in listOf(balance.replace("hyper", "codex"), balance.replace("\"api\"", "\"web\""),
            "[]", "not JSON")) {
            assertTrue(parse(capture(body)) is Result.Failure)
        }
        assertTrue(parse(capture(balance, 139)) is Result.Failure)
    }

    @Test fun mapsAuthenticationRateLimitAndServiceErrorsWithoutExposingSecrets() {
        val body = """[{"provider":"hyper","source":"api","error":{
            "code":1,"kind":"provider","message":"Charm Hyper API key rejected (HTTP 401). synthetic-secret"}}]"""
        val result = parse(capture(body, 1)) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        assertTrue((result.error as AppError.AuthError).isTerminal)
        assertFalse(result.error.toString().contains("synthetic-secret"))
        val forbidden = parse(capture(body.replace("Charm Hyper API key rejected (HTTP 401). synthetic-secret", "Charm Hyper API key cannot access credits (HTTP 403)."), 1)) as Result.Failure
        assertTrue(forbidden.error is AppError.AuthError)
        // The plugin's rate-limit text carries no "HTTP 429" marker, so it needs its own match.
        val rate = parse(capture(body.replace("Charm Hyper API key rejected (HTTP 401). synthetic-secret", "Charm Hyper credits requests are rate limited."), 1)) as Result.Failure
        assertTrue(rate.error is AppError.RateLimited)
        val service = parse(capture(body.replace("Charm Hyper API key rejected (HTTP 401). synthetic-secret", "Charm Hyper API error: HTTP 503"), 1)) as Result.Failure
        assertTrue(service.error is AppError.ServiceUnavailable)
    }
}
