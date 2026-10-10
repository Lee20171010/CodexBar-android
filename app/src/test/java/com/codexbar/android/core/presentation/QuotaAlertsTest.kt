package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowSlot
import com.codexbar.android.core.notification.AlertReading
import com.codexbar.android.core.notification.AlertSettings
import com.codexbar.android.core.notification.QuotaAlertKind
import com.codexbar.android.core.notification.QuotaAlerts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class QuotaAlertsTest {
    private val all = AlertSettings(depleted = true, low = true, pace = true)
    private val now = Instant.parse("2026-10-07T12:00:00Z").epochSecond
    private val defaultZone = TimeZone.getDefault()

    // workday pace classification reads ZoneId.systemDefault(); pin UTC so results are deterministic.
    @Before fun forceUtc() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() { TimeZone.setDefault(defaultZone) }

    private fun quota(used: Double, at: Long = now, resetsAt: Instant? = null, duration: Long? = null) =
        QuotaInfo(AiService.CODEX, listOf(UsageWindow("Weekly", used, resetsAt, durationSeconds = duration)),
            null, fetchedAt = Instant.ofEpochSecond(at))

    private fun paceQuota(used: Double, at: Long = now, resetsAt: Instant, duration: Long? = 604800,
        slot: UsageWindowSlot? = UsageWindowSlot.SECONDARY, service: AiService = AiService.CODEX) =
        QuotaInfo(service, listOf(UsageWindow("Weekly", used, resetsAt, durationSeconds = duration, slot = slot)),
            null, fetchedAt = Instant.ofEpochSecond(at))

    @Test fun `low alert fires once and rearms after recovery above threshold`() {
        val low = all.copy(depleted = false, pace = false)
        val (state, first) = QuotaAlerts.observe(emptyMap(), quota(0.85), now, low)
        assertEquals(listOf(QuotaAlertKind.LOW), first.map { it.second })
        val (_, repeat) = QuotaAlerts.observe(state, quota(0.86, at = now + 60), now + 60, low)
        assertEquals(emptyList<Any>(), repeat)
        val (recovered, _) = QuotaAlerts.observe(state, quota(0.70, at = now + 120), now + 120, low)
        val (_, again) = QuotaAlerts.observe(recovered, quota(0.85, at = now + 180), now + 180, low)
        assertEquals(listOf(QuotaAlertKind.LOW), again.map { it.second })
    }

    @Test fun `depleted alert fires once and rearms below ninety-five percent`() {
        val depleted = all.copy(low = false, pace = false)
        val (state, first) = QuotaAlerts.observe(emptyMap(), quota(1.0), now, depleted)
        assertEquals(listOf(QuotaAlertKind.DEPLETED), first.map { it.second })
        val (reset, repeat) = QuotaAlerts.observe(state, quota(0.90, at = now + 60), now + 60, depleted)
        assertEquals(emptyList<Any>(), repeat)
        val (_, again) = QuotaAlerts.observe(reset, quota(1.0, at = now + 120), now + 120, depleted)
        assertEquals(listOf(QuotaAlertKind.DEPLETED), again.map { it.second })
    }

    @Test fun `pace alert fires once per cycle when projected to run out early`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val (state, first) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        val (_, repeat) = QuotaAlerts.observe(state, paceQuota(0.52, at = now + 60, resetsAt = reset), now + 60, pace)
        assertEquals(emptyList<Any>(), repeat)
        // Duplicate timestamp keeps the prior receipt untouched.
        val (_, dup) = QuotaAlerts.observe(state, paceQuota(0.52, resetsAt = reset), now, pace)
        assertEquals(emptyList<Any>(), dup)
    }

    @Test fun `disabled settings emit nothing and stale readings are ignored`() {
        val (_, none) = QuotaAlerts.observe(emptyMap(), quota(1.0), now, AlertSettings(false, false, false))
        assertEquals(emptyList<Any>(), none)
        val stale = quota(1.0, at = now - 301)
        val (state, late) = QuotaAlerts.observe(emptyMap(), stale, now, all)
        assertEquals(emptyList<Any>(), late)
        assertEquals(emptyMap<String, AlertReading>(), state)
    }

    @Test fun `pace uses effective workDays like the detail view`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z") // Monday; cycle starts Monday 2026-10-05
        // Linear expectation: 35.7% elapsed at 50% used is ahead of pace.
        val (_, linear) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), linear.map { it.second })
        // Mon-Fri expectation: 2.5 of 5 workdays elapsed at 50% used is exactly on track.
        val (state, workday) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace, workDays = 5)
        assertEquals(emptyList<Any>(), workday)
        assertEquals(0L, state.getValue("Weekly").paceNotifiedAt)
    }

    @Test fun `overquota depletion fires once and survives malformed readings`() {
        val depleted = all.copy(low = false, pace = false)
        val (state, first) = QuotaAlerts.observe(emptyMap(), quota(1.10), now, depleted)
        assertEquals(listOf(QuotaAlertKind.DEPLETED), first.map { it.second })
        val (_, repeat) = QuotaAlerts.observe(state, quota(1.10, at = now + 60), now + 60, depleted)
        assertEquals(emptyList<Any>(), repeat)
        val (malformed, none) = QuotaAlerts.observe(state, quota(Double.NaN, at = now + 120), now + 120, depleted)
        assertEquals(emptyList<Any>(), none)
        val (negative, _) = QuotaAlerts.observe(malformed, quota(-0.5, at = now + 180), now + 180, depleted)
        // Malformed readings retained the receipt: no re-alert, and rearm still works from it.
        val (_, afterBad) = QuotaAlerts.observe(negative, quota(1.10, at = now + 240), now + 240, depleted)
        assertEquals(emptyList<Any>(), afterBad)
        val (rearmed, _) = QuotaAlerts.observe(negative, quota(0.90, at = now + 300), now + 300, depleted)
        val (_, again) = QuotaAlerts.observe(rearmed, quota(1.10, at = now + 360), now + 360, depleted)
        assertEquals(listOf(QuotaAlertKind.DEPLETED), again.map { it.second })
    }

    @Test fun `pace alerts cover only Codex and Claude primary and secondary lanes`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        for ((service, slot) in listOf(
            AiService.GEMINI to UsageWindowSlot.SECONDARY,
            AiService.CODEX to UsageWindowSlot.TERTIARY,
            AiService.CLAUDE to null)) {
            val (_, alerts) = QuotaAlerts.observe(emptyMap(),
                paceQuota(0.50, resetsAt = reset, slot = slot, service = service), now, pace)
            assertEquals("$service/$slot must not alert", emptyList<Any>(), alerts)
        }
        val (_, claude) = QuotaAlerts.observe(emptyMap(),
            paceQuota(0.50, resetsAt = reset, service = AiService.CLAUDE), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), claude.map { it.second })
    }

    @Test fun `pace floor suppresses alerts before three percent of the cycle elapsed`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val early = reset.epochSecond - 604800 + 3600 // 1h of 7d elapsed → expected ≈0.6% < 3
        val (_, alerts) = QuotaAlerts.observe(emptyMap(),
            paceQuota(0.50, at = early, resetsAt = reset), early, pace)
        assertEquals(emptyList<Any>(), alerts)
    }

    @Test fun `recovered pace rearms within the same cycle`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val (fired, first) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        // 30% used at 35.7% elapsed projects to last → Mac removes the key (rearm).
        val (recovered, calm) = QuotaAlerts.observe(fired,
            paceQuota(0.30, at = now + 60, resetsAt = reset), now + 60, pace)
        assertEquals(emptyList<Any>(), calm)
        assertEquals(0L, recovered.getValue("Weekly").paceNotifiedAt)
        val (_, again) = QuotaAlerts.observe(recovered,
            paceQuota(0.55, at = now + 120, resetsAt = reset), now + 120, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), again.map { it.second })
    }

    @Test fun `cycle rollover allows a new pace alert`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val (fired, first) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        val (_, rolled) = QuotaAlerts.observe(fired,
            paceQuota(0.50, at = now + 604800, resetsAt = reset.plusSeconds(604800)), now + 604800, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), rolled.map { it.second })
    }

    @Test fun `reset corrections stay in-cycle on both sides and roll over at the tolerance edge`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val (fired, first) = QuotaAlerts.observe(emptyMap(), paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        val (_, plus) = QuotaAlerts.observe(fired,
            paceQuota(0.52, at = now + 60, resetsAt = reset.plusSeconds(1200)), now + 60, pace)
        assertEquals(emptyList<Any>(), plus)
        val (_, minus) = QuotaAlerts.observe(fired,
            paceQuota(0.52, at = now + 60, resetsAt = reset.minusSeconds(1200)), now + 60, pace)
        assertEquals(emptyList<Any>(), minus)
        // Weekly tolerance = max(604800/2, 300) = 302400, strict <: one second inside stays,
        // the exact boundary is a new cycle. Shifting now with resetsAt keeps the elapsed fraction.
        val inside = 302399L
        val (_, edgeInside) = QuotaAlerts.observe(fired,
            paceQuota(0.50, at = now + inside, resetsAt = reset.plusSeconds(inside)), now + inside, pace)
        assertEquals(emptyList<Any>(), edgeInside)
        val edge = 302400L
        val (_, edgeAlerts) = QuotaAlerts.observe(fired,
            paceQuota(0.50, at = now + edge, resetsAt = reset.plusSeconds(edge)), now + edge, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), edgeAlerts.map { it.second })
    }

    @Test fun `session lane dedups on its own cycle and survives reset corrections`() {
        val pace = all.copy(depleted = false, low = false)
        val sessionReset = Instant.parse("2026-10-07T13:00:00Z") // 5h window starting 08:00
        val at = Instant.parse("2026-10-07T09:00:00Z").epochSecond // 20% elapsed, realistic
        val (fired, first) = QuotaAlerts.observe(emptyMap(),
            paceQuota(0.50, at = at, resetsAt = sessionReset, duration = 18000,
                slot = UsageWindowSlot.PRIMARY), at, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        // Parent counterexample: +20min reset correction must not refire (old 604800 fallback did).
        val (_, corrected) = QuotaAlerts.observe(fired,
            paceQuota(0.52, at = at + 60, resetsAt = sessionReset.plusSeconds(1200), duration = 18000,
                slot = UsageWindowSlot.PRIMARY), at + 60, pace)
        assertEquals(emptyList<Any>(), corrected)
        val (_, rolled) = QuotaAlerts.observe(fired,
            paceQuota(0.50, at = at + 18000, resetsAt = sessionReset.plusSeconds(18000), duration = 18000,
                slot = UsageWindowSlot.PRIMARY), at + 18000, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), rolled.map { it.second })
    }

    @Test fun `absent source duration uses the 300s nil-tolerance identity`() {
        val pace = all.copy(depleted = false, low = false)
        val sessionReset = Instant.parse("2026-10-07T13:00:00Z")
        val at = Instant.parse("2026-10-07T09:00:00Z").epochSecond
        val (fired, first) = QuotaAlerts.observe(emptyMap(),
            paceQuota(0.50, at = at, resetsAt = sessionReset, duration = null,
                slot = UsageWindowSlot.PRIMARY), at, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        assertEquals(null, fired.getValue("Weekly").paceCycleDuration)
        // Mac stores the optional source duration; nil tolerance is 300s strict, not the
        // PacePolicy math fallback. 299s either side stays in-cycle, exactly 300s is new.
        for (shift in listOf(299L, -299L)) {
            val (_, inside) = QuotaAlerts.observe(fired,
                paceQuota(0.52, at = at + 60, resetsAt = sessionReset.plusSeconds(shift), duration = null,
                    slot = UsageWindowSlot.PRIMARY), at + 60, pace)
            assertEquals("$shift s must stay in-cycle", emptyList<Any>(), inside)
        }
        val (_, edge) = QuotaAlerts.observe(fired,
            paceQuota(0.52, at = at + 60, resetsAt = sessionReset.minusSeconds(300), duration = null,
                slot = UsageWindowSlot.PRIMARY), at + 60, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), edge.map { it.second })
    }

    @Test fun `legacy pace receipt migrates conservatively without refiring`() {
        val pace = all.copy(depleted = false, low = false)
        val reset = Instant.parse("2026-10-12T00:00:00Z")
        val legacy = mapOf("Weekly" to AlertReading(at = now - 60, used = 0.45, paceNotifiedAt = now - 60))
        val (state, alerts) = QuotaAlerts.observe(legacy, paceQuota(0.50, resetsAt = reset), now, pace)
        assertEquals(emptyList<Any>(), alerts)
        val receipt = state.getValue("Weekly")
        assertEquals(reset.epochSecond, receipt.paceCycleResetsAt)
        assertEquals(604800L, receipt.paceCycleDuration)
    }
}
