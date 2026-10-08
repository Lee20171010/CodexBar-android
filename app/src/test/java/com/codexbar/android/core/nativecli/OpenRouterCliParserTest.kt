package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class OpenRouterCliParserTest {
    private val body = """[{"provider":"openrouter","source":"api","usage":{
        "primary":{"usedPercent":0,"isSyntheticPlaceholder":true},
        "providerCost":{"used":12.5,"limit":0,"balance":0,"currencyCode":"USD",
            "period":"This month (API key)","updatedAt":"2026-10-08T01:00:00Z"},
        "updatedAt":"2026-10-08T01:01:00Z"}}]"""
    private fun capture(text: String, exit: Int = 0) = CliProcess.Result(exit, false, 1, text, "", false, false)

    @Test fun `balance only result never invents quota and preserves zero and measurement age`() {
        val quota = (OpenRouterCliParser.parse(capture(body)) as Result.Success).value
        assertEquals(AiService.OPENROUTER, quota.service)
        assertTrue(quota.windows.isEmpty())
        assertEquals(0.0, quota.money!!.balance!!, 0.0)
        assertEquals(12.5, quota.money.spent!!, 0.0)
        assertEquals(Instant.parse("2026-10-08T01:00:00Z"), quota.money.fetchedAt)
        val missing = (OpenRouterCliParser.parse(capture(body.replace("\"balance\":0", "\"balance\":null"))) as Result.Success).value
        assertNull(missing.money!!.balance)
        assertTrue(missing.money.balanceText().contains("unavailable"))
        assertFalse(quota.money.balanceText().contains("unavailable"))
    }

    @Test fun `capped key is a budget with stable identity and no invented reset`() {
        val text = """[{"provider":"openrouter","source":"api","usage":{
            "primary":{"usedPercent":25},"updatedAt":"2026-10-08T01:00:00Z"}}]"""
        val quota = (OpenRouterCliParser.parse(capture(text)) as Result.Success).value
        assertEquals(UsageWindowKind.BUDGET, quota.windows.single().kind)
        assertEquals("api-key-budget", quota.windows.single().id)
        assertEquals(0.25, quota.windows.single().utilization, 0.0)
        assertNull(quota.windows.single().resetsAt)
        assertNull(quota.money!!.balance)
        assertNull(quota.money.spent)
    }

    @Test fun `rejects invalid envelopes money and failed processes`() {
        for (text in listOf(body.replace("openrouter", "codex"), body.replace("\"api\"", "\"web\""),
            body.replace("12.5", "-1"), body.replace("12.5", "1e999"), body.replace("USD", "UNKNOWN"),
            body.replace("\"balance\":0", "\"balance\":\"invalid\""), "[]", "invalid")) {
            assertTrue(OpenRouterCliParser.parse(capture(text)) is Result.Failure)
        }
        assertTrue(OpenRouterCliParser.parse(capture(body, 1)) is Result.Failure)
        assertTrue(OpenRouterCliParser.parse(CliProcess.Result(0, true, 1, body, "", false, false)) is Result.Failure)
        assertTrue(OpenRouterCliParser.parse(CliProcess.Result(0, false, 1, body, "", true, false)) is Result.Failure)
    }

    @Test fun `maps auth rate limits and service failures without leaking server text`() {
        for ((code, expected) in listOf(401 to AppError.AuthError::class, 403 to AppError.AuthError::class, 429 to AppError.RateLimited::class,
            503 to AppError.ServiceUnavailable::class, 500 to AppError.NetworkError::class)) {
            val text = """[{"provider":"openrouter","source":"api","error":{"message":"HTTP $code synthetic-secret"}}]"""
            val error = (OpenRouterCliParser.parse(capture(text, 1)) as Result.Failure).error
            assertEquals(expected, error::class)
            assertFalse(error.toString().contains("synthetic-secret"))
        }
    }
}
