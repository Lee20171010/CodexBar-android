package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageDataConfidence
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class PacePolicyTest {
    private val now: Instant = Instant.parse("2026-10-10T00:00:00Z")

    private fun quota(service: AiService, window: UsageWindow, confidence: UsageDataConfidence? = null) =
        QuotaInfo(service, listOf(window), null, fetchedAt = now, dataConfidence = confidence)

    private fun window(util: Double, duration: Long? = null, resets: Instant? = now.plusSeconds(6 * 86_400),
        slot: UsageWindowSlot? = null, supplemental: Boolean = false) =
        UsageWindow("w", util, resets, durationSeconds = duration, slot = slot, supplemental = supplemental)

    private fun pace(service: AiService, window: UsageWindow, at: Instant = now,
        confidence: UsageDataConfidence? = null) =
        PacePolicy.pace(quota(service, window, confidence), window, at, zone = ZoneOffset.UTC)

    @Test fun `providers without Mac pace capability never pace`() {
        for (service in listOf(AiService.GEMINI, AiService.DEEPSEEK, AiService.OPENROUTER)) {
            assertNull(service.name, pace(service, window(0.5, 18_000, now.plusSeconds(3 * 3600), UsageWindowSlot.PRIMARY)))
        }
    }

    @Test fun `session lane defaults a missing duration to five hours`() {
        val pace = pace(AiService.CODEX, window(0.5, null, now.plusSeconds(3 * 3600), UsageWindowSlot.PRIMARY))!!
        assertEquals(40.0, pace.expectedUsedPercent, 0.001) // 7200s elapsed of the 18000s default
    }

    @Test fun `weekly-length primary fails the session rule`() {
        assertNull(pace(AiService.CODEX, window(0.5, 604_800, now.plusSeconds(6 * 86_400), UsageWindowSlot.PRIMARY)))
    }

    @Test fun `codex weekly keeps the weekly default and generic weekly suppresses it`() {
        val codex = pace(AiService.CODEX, window(0.5, null, now.plusSeconds(6 * 86_400), UsageWindowSlot.SECONDARY))!!
        assertEquals(86_400.0 / 604_800.0 * 100.0, codex.expectedUsedPercent, 0.001)
        assertNull(pace(AiService.CLAUDE, window(0.5, null, now.plusSeconds(6 * 86_400), UsageWindowSlot.SECONDARY)))
        assertNull(pace(AiService.OPENCODE_GO, window(0.5, null, now.plusSeconds(6 * 86_400), UsageWindowSlot.SECONDARY)))
        val claude = pace(AiService.CLAUDE, window(0.5, 604_800, now.plusSeconds(6 * 86_400), UsageWindowSlot.SECONDARY))
        assertNotNull(claude)
        assertNotNull(pace(AiService.CLAUDE, window(0.5, 604_800, now.plusSeconds(6 * 86_400), UsageWindowSlot.TERTIARY)))
    }

    @Test fun `estimated payloads only block OpenCode Go`() {
        val go = window(0.5, 18_000, now.plusSeconds(3 * 3600), UsageWindowSlot.PRIMARY)
        assertNull(pace(AiService.OPENCODE_GO, go, confidence = UsageDataConfidence.ESTIMATED))
        assertNotNull(pace(AiService.OPENCODE_GO, go, confidence = UsageDataConfidence.EXACT))
        assertNotNull(pace(AiService.OPENCODE_GO, go, confidence = null)) // unknown is not estimated
        assertNotNull(pace(AiService.CODEX, go, confidence = UsageDataConfidence.ESTIMATED))
    }

    @Test fun `copilot infers the calendar month from the reset date`() {
        val at = Instant.parse("2026-10-11T00:00:00Z")
        val pace = pace(AiService.COPILOT, window(0.5, null, Instant.parse("2026-11-10T00:00:00Z")), at)!!
        assertEquals(86_400.0 / 2_678_400.0 * 100.0, pace.expectedUsedPercent, 0.01) // Oct 10 -> Nov 10 = 31d
    }

    @Test fun `go sentinel expands to the real month including long months`() {
        val sentinel = window(0.5, 2_592_000, Instant.parse("2026-04-01T00:00:00Z"), UsageWindowSlot.TERTIARY)
        val at = Instant.parse("2026-03-01T23:00:00Z") // day 1 of a 31-day cycle: raw sentinel scoring rejects it
        assertNull(QuotaPace.weeklyPace(sentinel, at, zone = ZoneOffset.UTC))
        val pace = pace(AiService.OPENCODE_GO, sentinel, at)!!
        assertEquals(82_800.0 / 2_678_400.0 * 100.0, pace.expectedUsedPercent, 0.01)
        val february = pace(AiService.OPENCODE_GO,
            window(0.5, 2_592_000, Instant.parse("2026-03-01T00:00:00Z"), UsageWindowSlot.TERTIARY),
            Instant.parse("2026-02-02T00:00:00Z"))!!
        assertEquals(86_400.0 / 2_419_200.0 * 100.0, february.expectedUsedPercent, 0.01) // 28-day February
    }

    @Test fun `copilot reset window keeps the eta exemption the lane gates do not have`() {
        val at = Instant.parse("2026-10-07T01:00:00Z") // 1h into the inferred Oct 7 -> Nov 7 month: expected < 3
        assertNotNull(pace(AiService.COPILOT, window(0.999999, null, Instant.parse("2026-11-07T00:00:00Z")), at))
        // Same near-exhausted shape on a weekly lane: expected < 3 suppresses it despite eta == 0.
        val weekly = window(0.999999, 604_800, now.plusSeconds(604_800 - 3_600), UsageWindowSlot.SECONDARY)
        assertNull(pace(AiService.CODEX, weekly))
    }

    @Test fun `lanes gate the first three percent`() {
        val resets = now.plusSeconds(18_000)
        assertNull(pace(AiService.CODEX, window(0.5, null, resets, UsageWindowSlot.PRIMARY), resets.minusSeconds(17_700)))
        assertNotNull(pace(AiService.CODEX, window(0.5, null, resets, UsageWindowSlot.PRIMARY), resets.minusSeconds(5_400)))
    }

    @Test fun `extras exhausted windows and missing resets never pace`() {
        assertNull(pace(AiService.CODEX, window(0.5, 18_000, now.plusSeconds(3 * 3600), null, supplemental = true)))
        assertNull(pace(AiService.CODEX, window(1.0, 18_000, now.plusSeconds(3 * 3600), UsageWindowSlot.PRIMARY)))
        assertNull(pace(AiService.CODEX, window(0.5, 18_000, null, UsageWindowSlot.PRIMARY)))
    }

    @Test fun `budgets supplemental and negative utilization never pace`() {
        val resets = now.plusSeconds(6 * 86_400)
        // Copilot monetary budgets carry a reset date but are not rate windows.
        assertNull(pace(AiService.COPILOT, UsageWindow("Budget", 0.5, resets, kind = UsageWindowKind.BUDGET)))
        assertNull(pace(AiService.COPILOT, UsageWindow("Extra", 0.5, resets, supplemental = true)))
        assertNull(pace(AiService.CODEX, UsageWindow("w", -0.5, resets, durationSeconds = 18_000, slot = UsageWindowSlot.PRIMARY)))
        assertNull(pace(AiService.CODEX, UsageWindow("w", Double.NaN, resets, durationSeconds = 18_000, slot = UsageWindowSlot.PRIMARY)))
    }

    @Test fun `copilot weekly window with explicit duration paces`() {
        assertNotNull(pace(AiService.COPILOT, window(0.5, 604_800, now.plusSeconds(6 * 86_400))))
    }
}
