package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.notification.AlertReading
import com.codexbar.android.core.notification.AlertSettings
import com.codexbar.android.core.notification.QuotaAlertKind
import com.codexbar.android.core.notification.QuotaAlerts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QuotaAlertsTest {
    private val all = AlertSettings(depleted = true, low = true, pace = true)
    private val now = Instant.parse("2026-10-07T12:00:00Z").epochSecond

    private fun quota(used: Double, at: Long = now, resetsAt: Instant? = null, duration: Long? = null) =
        QuotaInfo(AiService.CODEX, listOf(UsageWindow("Weekly", used, resetsAt, durationSeconds = duration)),
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
        val window = quota(0.50, resetsAt = reset, duration = 604800)
        val (state, first) = QuotaAlerts.observe(emptyMap(), window, now, pace)
        assertEquals(listOf(QuotaAlertKind.PACE), first.map { it.second })
        val (_, repeat) = QuotaAlerts.observe(state, quota(0.52, at = now + 60, resetsAt = reset, duration = 604800),
            now + 60, pace)
        assertEquals(emptyList<Any>(), repeat)
    }

    @Test fun `disabled settings emit nothing and stale readings are ignored`() {
        val (_, none) = QuotaAlerts.observe(emptyMap(), quota(1.0), now, AlertSettings(false, false, false))
        assertEquals(emptyList<Any>(), none)
        val stale = quota(1.0, at = now - 301)
        val (state, late) = QuotaAlerts.observe(emptyMap(), stale, now, all)
        assertEquals(emptyList<Any>(), late)
        assertEquals(emptyMap<String, AlertReading>(), state)
    }
}
