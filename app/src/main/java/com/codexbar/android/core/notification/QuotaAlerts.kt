package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.UsageWindowSlot
import com.codexbar.android.core.presentation.PacePolicy
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

enum class QuotaAlertKind { DEPLETED, LOW, PACE }

@Serializable
data class AlertReading(
    val at: Long,
    val used: Double,
    val lowNotified: Boolean = false,
    val depletedNotified: Boolean = false,
    val paceNotifiedAt: Long = 0,
    /** Reset instant of the cycle owning the last PACE alert (0 = none or legacy receipt). */
    val paceCycleResetsAt: Long = 0,
    /** Source window duration at alert time; null = source duration absent (Mac keeps it optional). */
    val paceCycleDuration: Long? = null
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
                var paceCycleResetsAt = old?.paceCycleResetsAt ?: 0
                var paceCycleDuration = old?.paceCycleDuration
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
                // Mac predictive alerts (PredictivePaceWarnings.swift): Codex/Claude session+weekly
                // lanes only, fire on !willLastToReset && eta > 0 (probability stays nil→1 without a
                // history dataset), rearm as soon as pace recovers within the cycle.
                if (settings.pace && window.resetsAt != null &&
                    (quota.service == AiService.CODEX || quota.service == AiService.CLAUDE) &&
                    (window.slot == UsageWindowSlot.PRIMARY || window.slot == UsageWindowSlot.SECONDARY)
                ) {
                    val pace = PacePolicy.pace(quota, window, Instant.ofEpochSecond(measured), workDays)
                    if (pace != null) {
                        // Mac stores the optional source windowMinutes in the dedup key; the
                        // resolved PacePolicy fallbacks (18000/604800) are math-only, never identity.
                        val sourceSeconds = window.durationSeconds
                        if (pace.willLastToReset) {
                            paceNotifiedAt = 0; paceCycleResetsAt = 0; paceCycleDuration = null
                        } else {
                            // Migrate a legacy receipt once, conservatively: its cycle is treated
                            // as already alerted instead of discarded.
                            if (paceNotifiedAt > 0 && paceCycleResetsAt == 0L) {
                                paceCycleResetsAt = window.resetsAt.epochSecond
                                paceCycleDuration = sourceSeconds
                            }
                            // Mac belongsToSameCycle: nullable duration equality, tolerance
                            // duration/2 (min 300s), 300s when the source duration is absent, strict <.
                            val sameCycle = paceNotifiedAt > 0 && paceCycleDuration == sourceSeconds &&
                                abs(window.resetsAt.epochSecond - paceCycleResetsAt) <
                                    (sourceSeconds?.let { max(it / 2, 300L) } ?: 300L)
                            if (sameCycle && paceCycleResetsAt != window.resetsAt.epochSecond) {
                                // Follow small provider reset-time corrections without re-alerting
                                // (Mac reconcileSiblingWindowKeys key migration).
                                paceCycleResetsAt = window.resetsAt.epochSecond
                            }
                            val eta = pace.etaSeconds
                            if (!sameCycle && eta != null && eta > 0) {
                                paceNotifiedAt = measured
                                paceCycleResetsAt = window.resetsAt.epochSecond
                                paceCycleDuration = sourceSeconds
                                alerts += window to QuotaAlertKind.PACE
                            }
                        }
                    }
                }
                next[window.id] = AlertReading(measured, used, lowNotified, depletedNotified,
                    paceNotifiedAt, paceCycleResetsAt, paceCycleDuration)
            }
        return next to alerts
    }
}
