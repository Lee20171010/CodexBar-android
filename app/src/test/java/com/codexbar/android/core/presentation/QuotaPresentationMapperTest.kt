package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountBalance
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.CodexResetCredits
import com.codexbar.android.core.domain.model.CodexContextUsage
import com.codexbar.android.core.domain.model.CodexDailyTokenUsage
import com.codexbar.android.core.domain.model.CodexModelTokenUsage
import com.codexbar.android.core.domain.model.CodexTelemetry
import com.codexbar.android.core.domain.model.CodexTokenTotals
import com.codexbar.android.core.domain.model.CodexTokenUsage
import com.codexbar.android.core.domain.model.ExtraUsage
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaNotice
import com.codexbar.android.core.domain.model.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class QuotaPresentationMapperTest {
    private val now = Instant.parse("2026-07-13T00:00:00Z")
    private val mapper = QuotaPresentationMapper(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `maps usage direction once for every surface`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = listOf(
                        UsageWindow("5h", 0.72, now.plusSeconds(7200)),
                        UsageWindow("weekly", 0.24, now.plusSeconds(172800))
                    ),
                    extraUsage = null,
                    tier = "Plus",
                    fetchedAt = now.minusSeconds(90)
                )
            ),
            locale = Locale.US
        )

        val service = snapshot.services.single()
        val primary = service.primaryMetric!!
        assertEquals("5h", primary.label)
        assertEquals(72, primary.usedPercent)
        assertEquals(28, primary.remainingPercent)
        assertEquals(0.28f, primary.barProgress, 0.001f)
        assertEquals("28% left", primary.remainingLabel)
        assertEquals("Resets in 2h 0m", primary.resetLabel)
        val resetPlan = requireNotNull(primary.resetPlan)
        assertEquals("Use by Jul 13, 02:00 UTC", resetPlan.deadlineLabel)
        assertEquals("Balanced budget: 14.0%/h", resetPlan.budgetLabel)
        assertEquals(
            "Best time to use it: now. Up to 28% is available before Jul 13, 02:00 UTC.",
            resetPlan.actionLabel
        )
        assertEquals("Use now · 28% available", resetPlan.compactActionLabel)
        assertEquals("1m ago", service.freshness.ageLabel)
    }

    @Test
    fun `does not render error data as zero quota`() {
        val snapshot = mapper.map(
            quotas = emptyList(),
            errors = mapOf(AiService.GEMINI to AppError.RateLimited(now.plusSeconds(600))),
            generatedAt = now
        )

        val service = snapshot.services.single()
        assertEquals(ServiceQuotaStatus.RateLimited, service.status)
        assertNull(service.primaryMetric)
        assertEquals("No fresh data", service.freshness.ageLabel)
        assertEquals("Rate limited until 2026-07-13T00:10:00Z", service.freshness.staleReason)
        assertEquals(now.plusSeconds(600), service.freshness.nextRetryAt)
    }

    @Test
    fun `balance-only success sorts before provider error`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.OPENCODE_GO,
                    windows = emptyList(),
                    extraUsage = null,
                    balance = AccountBalance(12.5, "USD"),
                    fetchedAt = now
                )
            ),
            errors = mapOf(AiService.CLAUDE to AppError.NetworkError("offline"))
        )

        assertEquals(AiService.OPENCODE_GO, snapshot.services.first().service)
    }

    @Test
    fun `redaction removes sensitive values while preserving service shape`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CLAUDE,
                    windows = listOf(UsageWindow("weekly", 0.91, now.plusSeconds(3600))),
                    extraUsage = ExtraUsage(
                        isEnabled = true,
                        monthlyLimit = 100.0,
                        usedCredits = 91.0,
                        utilization = 0.91,
                        currency = "USD"
                    ),
                    tier = "Max",
                    fetchedAt = now
                )
            ),
            privacy = PrivacyPresentation(redactSensitiveValues = true)
        )

        val service = snapshot.services.single()
        val metric = service.metrics.single()
        assertEquals(ServiceQuotaStatus.Redacted, service.status)
        assertNull(service.tier)
        assertNull(metric.usedPercent)
        assertEquals("Remaining hidden", metric.remainingLabel)
        assertEquals(QuotaSeverity.Redacted, metric.severity)
        assertNull(metric.resetPlan)
        assertEquals("Used hidden", service.extraUsage!!.usedCreditsLabel)
    }

    @Test
    fun `reset plans are limited to Codex windows`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CLAUDE,
                    windows = listOf(UsageWindow("5h", 0.25, now.plusSeconds(7200))),
                    extraUsage = null,
                    fetchedAt = now
                )
            )
        )

        assertNull(snapshot.services.single().metrics.single().resetPlan)
    }

    @Test
    fun `maps a missing short-term window into a positive dashboard insight`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = listOf(UsageWindow("7-Day", 0.24, now.plusSeconds(172800))),
                    extraUsage = null,
                    fetchedAt = now,
                    notices = setOf(QuotaNotice.WindowLimitNotProvided(18000L))
                )
            )
        )

        val insight = snapshot.services.single().insights.single()
        assertEquals("5-hour limit isn't active right now!", insight.title)
        assertEquals(
            "No short-term window was returned, so you can focus on the longer window and keep building.",
            insight.message
        )
    }

    @Test
    fun `maps Codex reset credits with count and expiration order`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = listOf(UsageWindow("7-Day", 0.24, now.plusSeconds(172800))),
                    extraUsage = null,
                    fetchedAt = now,
                    codexResetCredits = CodexResetCredits(
                        availableCount = 3,
                        expiresAt = listOf(
                            now.plusSeconds(7200),
                            now.plusSeconds(86400)
                        )
                    )
                )
            )
        )

        val credits = requireNotNull(snapshot.services.single().codexResetCredits)
        assertEquals(3, credits.availableCount)
        assertEquals("3 available", credits.availableLabel)
        assertEquals("Next expires Jul 13, 02:00 UTC", credits.nextExpiryLabel)
        assertEquals(
            listOf("Expires Jul 13, 02:00 UTC", "Expires Jul 14, 00:00 UTC"),
            credits.expiryLabels
        )
        assertEquals(1, credits.noExpiryCount)
    }

    @Test
    fun `redaction hides Codex reset credit inventory`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = emptyList(),
                    extraUsage = null,
                    fetchedAt = now,
                    codexResetCredits = CodexResetCredits(
                        availableCount = 1,
                        expiresAt = emptyList()
                    )
                )
            ),
            privacy = PrivacyPresentation(redactSensitiveValues = true)
        )

        assertNull(snapshot.services.single().codexResetCredits)
    }

    @Test
    fun `maps Codex context and token telemetry with numeric labels`() {
        val totals = CodexTokenTotals(12_000, 8_000, 500, 200, 12_500)
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = emptyList(),
                    extraUsage = null,
                    fetchedAt = now,
                    codexTelemetry = CodexTelemetry(
                        generatedAt = now,
                        currentContext = CodexContextUsage(
                            capturedAt = now.minusSeconds(30),
                            model = "gpt-5.6",
                            usedTokens = 64_600,
                            contextWindowTokens = 258_400,
                            sessionTokens = 125_000
                        ),
                        tokenUsage = CodexTokenUsage(
                            today = totals,
                            last7Days = totals.copy(totalTokens = 25_000, inputTokens = 24_000, outputTokens = 1_000),
                            last30Days = totals.copy(totalTokens = 50_000, inputTokens = 48_000, outputTokens = 2_000),
                            daily = listOf(CodexDailyTokenUsage(LocalDate.parse("2026-07-13"), totals)),
                            models = listOf(CodexModelTokenUsage("gpt-5.6", totals))
                        )
                    )
                )
            ),
            locale = Locale.US
        )

        val telemetry = requireNotNull(snapshot.services.single().codexTelemetry)
        assertEquals(25, telemetry.currentContext?.usedPercent)
        assertEquals("64,600 / 258,400", telemetry.currentContext?.usageLabel)
        assertEquals("12,500", telemetry.tokenUsage.today.totalLabel)
        assertEquals(0.25f, telemetry.tokenUsage.models.single().shareFraction, 0.001f)
    }

    @Test
    fun `redaction hides all local Codex telemetry`() {
        val totals = CodexTokenTotals(100, 20, 10, 3, 110)
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = emptyList(),
                    extraUsage = null,
                    fetchedAt = now,
                    codexTelemetry = CodexTelemetry(
                        generatedAt = now,
                        currentContext = null,
                        tokenUsage = CodexTokenUsage(totals, totals, totals, emptyList(), emptyList())
                    )
                )
            ),
            privacy = PrivacyPresentation(redactSensitiveValues = true)
        )

        assertNull(snapshot.services.single().codexTelemetry)
    }

    @Test
    fun `maps quota history and marks reset boundaries`() {
        val metricKey = QuotaPresentationMapper.metricKey(AiService.CODEX, "7-Day")
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = listOf(UsageWindow("7-Day", 0.1, now.plusSeconds(604800))),
                    extraUsage = null,
                    fetchedAt = now
                )
            ),
            historyByMetricKey = mapOf(
                metricKey to listOf(
                    QuotaHistorySample(now.minusSeconds(7200), 0.8, now.plusSeconds(3600)),
                    QuotaHistorySample(now.minusSeconds(3600), 0.1, now.plusSeconds(604800))
                )
            )
        )

        val points = snapshot.services.single().metrics.single().history.points
        assertEquals(2, points.size)
        assertEquals(0.8f, points.first().usedFraction)
        assertEquals(false, points.first().startsNewCycle)
        assertEquals(true, points.last().startsNewCycle)
    }

    @Test
    fun `redaction removes quota history`() {
        val metricKey = QuotaPresentationMapper.metricKey(AiService.CLAUDE, "weekly")
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CLAUDE,
                    windows = listOf(UsageWindow("weekly", 0.5, now.plusSeconds(604800))),
                    extraUsage = null,
                    fetchedAt = now
                )
            ),
            privacy = PrivacyPresentation(redactSensitiveValues = true),
            historyByMetricKey = mapOf(
                metricKey to listOf(QuotaHistorySample(now, 0.5, now.plusSeconds(604800)))
            )
        )

        assertTrue(snapshot.services.single().metrics.single().history.points.isEmpty())
    }

    @Test
    fun `balance is labelled and redacted separately from credits`() {
        val quota = QuotaInfo(
            service = AiService.OPENCODE_GO,
            windows = emptyList(),
            extraUsage = null,
            balance = AccountBalance(12.5, "USD"),
            fetchedAt = now
        )

        val service = mapper.map(listOf(quota), privacy = PrivacyPresentation()).services.single()
        assertEquals("Zen balance", service.balance?.label)
        assertEquals("USD 12.50", service.balance?.amountLabel)

        val redacted = mapper.map(listOf(quota), privacy = PrivacyPresentation(true)).services.single()
        assertEquals("Zen balance", redacted.balance?.label)
        assertEquals("Balance hidden", redacted.balance?.amountLabel)
        assertNull(redacted.extraUsage)
    }

    @Test
    fun `renewal date is present normally and hidden by privacy`() {
        val quota = QuotaInfo(
            service = AiService.OPENCODE_GO,
            windows = emptyList(),
            extraUsage = null,
            renewsAt = Instant.parse("2026-08-01T00:00:00Z"),
            fetchedAt = now
        )

        assertNotNull(mapper.map(listOf(quota)).services.single().renewal)
        assertNull(
            mapper.map(listOf(quota), privacy = PrivacyPresentation(true)).services.single().renewal
        )
    }
}
