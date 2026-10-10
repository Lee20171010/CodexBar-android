package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.presentation.QuotaPace
import kotlinx.serialization.Serializable
import java.time.Instant

enum class QuotaAlertKind { DEPLETED, LOW, PACE }

@Serializable
data class AlertReading(
    val at: Long,
    val used: Double,
    val lowNotified: Boolean = false,
    val depletedNotified: Boolean = false,
    val paceNotifiedAt: Long = 0
)

data class AlertSettings(
    val depleted: Boolean,
    val low: Boolean,
    val pace: Boolean
)

/**
 * Usage-warning alerts mirroring the Mac notification split (quota_depleted, threshold_warnings,
 * predictive_pace_warnings). Pure and receipt-persisted like [QuotaRecovery] so a restart cannot
 * re-alert a window that was already reported in the current state or cycle.
 */
object QuotaAlerts {
    /** Mac default warning marker: alert at 20% remaining. */
    const val LOW_REMAINING = 0.20

    /** Rearm the low alert only after recovering 5 points above the threshold. */
    private const val HYSTERESIS = 0.05
    private const val DEPLETED_USED = 0.995
    private const val DEPLETED_REARM = 0.95
    /** Usage must exceed linear expectation by 6 points before a pace alert fires. */
    private const val PACE_AHEAD = 6.0

    fun observe(
        previous: Map<String, AlertReading>,
        quota: QuotaInfo,
        now: Long,
        settings: AlertSettings,
        workDays: Int? = null
    ): Pair<Map<String, AlertReading>, List<Pair<UsageWindow, QuotaAlertKind>>> {
        val measured = quota.fetchedAt.epochSecond
        if (measured !in (now - 300)..now || quota.source == "legacy") return previous to emptyList()
        val next = mutableMapOf<String, AlertReading>()
        val alerts = mutableListOf<Pair<UsageWindow, QuotaAlertKind>>()
        quota.windows.filter { it.kind == UsageWindowKind.QUOTA && !it.supplemental }
            .distinctBy { it.id }.take(32).forEach { window ->
                val used = window.utilization
                val old = previous[window.id]
                // Overquota is valid input; only non-finite or negative readings are malformed, and
                // they must retain the prior receipt so dedup and rearm survive a bad fetch.
                if (!used.isFinite() || used < 0 || window.id.length > 128) {
                    if (old != null) next[window.id] = old
                    return@forEach
                }
                if (old != null && measured <= old.at) { next[window.id] = old; return@forEach }
                var lowNotified = old?.lowNotified ?: false
                var depletedNotified = old?.depletedNotified ?: false
                var paceNotifiedAt = old?.paceNotifiedAt ?: 0
                if (lowNotified && used < 1.0 - LOW_REMAINING - HYSTERESIS) lowNotified = false
                if (depletedNotified && used < DEPLETED_REARM) depletedNotified = false
                if (settings.depleted && used >= DEPLETED_USED && !depletedNotified) {
                    depletedNotified = true
                    alerts += window to QuotaAlertKind.DEPLETED
                }
                if (settings.low && used >= 1.0 - LOW_REMAINING && !lowNotified) {
                    lowNotified = true
                    alerts += window to QuotaAlertKind.LOW
                }
                if (settings.pace && window.resetsAt != null) {
                    val pace = QuotaPace.weeklyPace(window, Instant.ofEpochSecond(measured), workDays)
                    val cycleStart = window.resetsAt.epochSecond -
                        (window.durationSeconds ?: 604800L)
                    if (pace != null && pace.deltaPercent > PACE_AHEAD &&
                        pace.etaSeconds != null && !pace.willLastToReset &&
                        paceNotifiedAt < cycleStart
                    ) {
                        paceNotifiedAt = measured
                        alerts += window to QuotaAlertKind.PACE
                    }
                }
                next[window.id] = AlertReading(measured, used, lowNotified, depletedNotified, paceNotifiedAt)
            }
        return next to alerts
    }
}
