package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.*
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CodexReportedCreditsTest {
    private val at = "2026-10-08T12:00:00Z"
    private fun balance(read: Boolean) = Json.parseToJsonElement("""{"remaining":0,"balanceReadSucceeded":$read,
        "updatedAt":"$at","codexCreditLimit":{"used":5,"limit":10,"remaining":5,"updatedAt":"$at"}}""")

    @Test fun `zero and unread balance differ while personal cap stays separate`() {
        val zero = CodexReportedCredits.balance(balance(true))!!
        val unknown = CodexReportedCredits.balance(balance(false))!!
        assertEquals(0.0, zero.balance!!, 0.0)
        assertNull(unknown.balance)
        assertEquals(5.0, unknown.cap!!.remaining, 0.0)
        assertNull(CodexReportedCredits.balance(null))
        val quota = QuotaInfo(AiService.CODEX, emptyList(), null, fetchedAt = Instant.parse(at), credits = unknown)
        assertEquals(quota, Json.decodeFromString<QuotaInfo>(Json.encodeToString(quota)))
    }

    @Test fun `inventory preserves type status expiry and distinguishes empty from unknown`() {
        val inventory = CodexReportedCredits.inventory(Json.parseToJsonElement("""{"availableCount":1,"updatedAt":"$at",
            "credits":[{"reset_type":"usage_limit","status":"available","expires_at":"2026-11-01T00:00:00Z"}]}"""))!!
        assertEquals(1, inventory.availableCount)
        assertEquals("usage_limit", inventory.items.single().type)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), inventory.items.single().expiresAt)
        assertEquals(0, CodexReportedCredits.inventory(Json.parseToJsonElement(
            """{"availableCount":0,"updatedAt":"$at","credits":[]}"""))!!.availableCount)
        assertNull(CodexReportedCredits.inventory(null))
    }

    @Test fun `malformed optional enrichment cannot fail valid quota`() {
        val json = """[{"provider":"codex","source":"oauth","credits":{"remaining":-1},
            "usage":{"primary":{"usedPercent":50},"updatedAt":"$at","codexResetCredits":{"credits":false}}}]"""
        val result = NativeQuotaCliParser.parse(CliProcess.Result(0, false, 0, json, "", false, false), AiService.CODEX)
        val quota = (result as Result.Success).value
        assertEquals(.5, quota.windows.single().utilization, 0.0)
        assertNull(quota.credits)
        assertNull(quota.resetInventory)
    }
}
