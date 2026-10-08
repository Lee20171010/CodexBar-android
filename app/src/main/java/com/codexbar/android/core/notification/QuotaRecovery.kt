package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import kotlinx.serialization.Serializable

@Serializable
data class RecoveryReading(
    val at: Long,
    val used: Double,
    val source: String,
    val notifiedUntil: Long = 0
)

object QuotaRecovery {
    /** Require measured movement out of a near-exhausted state; a moved reset date proves nothing. */
    fun observe(previous: Map<String, RecoveryReading>, quota: QuotaInfo, now: Long):
        Pair<Map<String, RecoveryReading>, List<UsageWindow>> {
        val measured = quota.fetchedAt.epochSecond
        if (measured !in (now - 300)..now || quota.source == "legacy") return previous to emptyList()
        val next = mutableMapOf<String, RecoveryReading>()
        val recovered = mutableListOf<UsageWindow>()
        quota.windows.filter { it.kind == UsageWindowKind.QUOTA && !it.supplemental }
            .distinctBy { it.id }.take(32).forEach { window ->
                if (!window.utilization.isFinite() || window.utilization < 0 || window.id.length > 128) return@forEach
                val old = previous[window.id]
                if (old != null && measured <= old.at) { next[window.id] = old; return@forEach }
                val duration = window.durationSeconds?.takeIf { it in 60..(31 * 86400L) }
                val recent = old != null && measured - old.at <= (duration ?: 86400).coerceAtMost(86400)
                val confirmed = recent && old!!.source == quota.source && old.used >= 0.95 &&
                    window.utilization <= 0.80 && now >= old.notifiedUntil
                // Rolling windows have no stable cycle boundary. Conservatively limit one alert
                // per reported duration (one day when unknown), even if reset timestamps drift.
                val until = if (confirmed) maxOf(now + (duration ?: 86400), window.resetsAt?.epochSecond ?: 0)
                    else old?.notifiedUntil ?: 0
                next[window.id] = RecoveryReading(measured, window.utilization, quota.source, until)
                if (confirmed) recovered += window
            }
        return next to recovered
    }
}
