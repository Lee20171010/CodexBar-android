package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.FreshnessPresentation
import com.codexbar.android.core.presentation.FreshnessState
import com.codexbar.android.core.presentation.PacePresentation
import com.codexbar.android.core.presentation.PaceState
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSummaryTest {

    @Test
    fun `the tightest window across every provider leads the summary`() {
        val summary = snapshot(
            service(AiService.CLAUDE, metrics = listOf(metric("5-Hour", used = 0.4))),
            service(
                AiService.CODEX,
                metrics = listOf(metric("5-Hour", used = 0.2), metric("7-Day", used = 0.91))
            )
        ).toDashboardSummary()

        val tightest = requireNotNull(summary.tightest)
        assertEquals(AiService.CODEX, tightest.service)
        assertEquals("7-Day", tightest.metricLabel)
        assertEquals(0.91, tightest.usedFraction, 0.0001)
        assertEquals(2, summary.connectedCount)
        assertEquals(0, summary.attentionCount)
    }

    @Test
    fun `a provider that cannot report is counted but never leads`() {
        val summary = snapshot(
            service(AiService.CLAUDE, metrics = listOf(metric("5-Hour", used = 0.1))),
            service(
                AiService.COPILOT,
                status = ServiceQuotaStatus.AuthRequired,
                metrics = listOf(metric("Monthly", used = 0.99))
            )
        ).toDashboardSummary()

        assertEquals(AiService.CLAUDE, requireNotNull(summary.tightest).service)
        assertEquals(1, summary.attentionCount)
        assertEquals(2, summary.connectedCount)
        assertTrue(summary.pulses.single { it.service == AiService.COPILOT }.needsAttention)
    }

    @Test
    fun `providers without a bounded window leave the headline empty`() {
        val summary = snapshot(
            service(AiService.OPENROUTER, metrics = emptyList())
        ).toDashboardSummary()

        assertNull(summary.tightest)
        assertEquals(1, summary.connectedCount)
        assertEquals(1, summary.pulses.size)
        assertNull(summary.pulses.single().remainingPercent)
    }

    @Test
    fun `every connected provider gets one pulse`() {
        val summary = snapshot(
            service(AiService.CLAUDE, metrics = listOf(metric("5-Hour", used = 0.25))),
            service(AiService.CODEX, metrics = listOf(metric("5-Hour", used = 0.9)))
        ).toDashboardSummary()

        assertEquals(listOf(AiService.CLAUDE, AiService.CODEX), summary.pulses.map { it.service })
        assertEquals(75, summary.pulses.first().remainingPercent)
        assertEquals(QuotaSeverity.Critical, summary.pulses.last().severity)
    }

    private fun snapshot(vararg services: ServiceQuotaPresentation) = QuotaPresentationSnapshot(
        generatedAt = Instant.ofEpochSecond(1_750_000_000L),
        services = services.toList()
    )

    private fun service(
        service: AiService,
        status: ServiceQuotaStatus = ServiceQuotaStatus.Fresh,
        metrics: List<QuotaMetricPresentation>
    ) = ServiceQuotaPresentation(
        service = service,
        accountLabel = null,
        tier = null,
        status = status,
        primaryMetric = metrics.maxByOrNull { it.usedFraction ?: 0.0 },
        metrics = metrics,
        extraUsage = null,
        freshness = FreshnessPresentation(
            fetchedAt = Instant.ofEpochSecond(1_750_000_000L),
            ageLabel = "just now",
            state = FreshnessState.Fresh
        ),
        supportedActions = emptySet()
    )

    private fun metric(label: String, used: Double) = QuotaMetricPresentation(
        id = label,
        label = label,
        usedFraction = used,
        remainingFraction = 1.0 - used,
        usedPercent = (used * 100).toInt(),
        remainingPercent = ((1.0 - used) * 100).toInt(),
        usedLabel = "${(used * 100).toInt()}% used",
        remainingLabel = "${((1.0 - used) * 100).toInt()}% left",
        barProgress = (1.0 - used).toFloat(),
        severity = when {
            used >= 0.85 -> QuotaSeverity.Critical
            used >= 0.60 -> QuotaSeverity.Warning
            else -> QuotaSeverity.Good
        },
        resetsAt = null,
        resetLabel = null,
        pace = PacePresentation(state = PaceState.Unknown, label = "")
    )
}
