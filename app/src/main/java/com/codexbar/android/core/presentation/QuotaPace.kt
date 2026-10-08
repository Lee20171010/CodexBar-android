package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import kotlinx.serialization.Serializable

@Serializable
data class PaceSample(val at: Long, val remaining: Double)

@Serializable
data class WindowHistory(
    val cycleEnd: Long?,
    val duration: Long?,
    val samples: List<PaceSample> = emptyList(),
    val source: String = ""
)

data class PaceEstimate(val samples: List<PaceSample>, val runsOutAt: Long?, val cycleEnd: Long?, val duration: Long?)

object QuotaPace {
    private const val MAX = 48

    /** Successful samples only. A new cycle, account or gap never continues the old line. */
    fun record(previous: Map<String, WindowHistory>, quota: QuotaInfo, now: Long): Map<String, WindowHistory> {
        val measured = quota.fetchedAt.epochSecond
        val next = retained(previous, now).toMutableMap()
        if (measured !in (now - 300)..now || quota.source == "legacy") return next
        quota.windows.filter { !it.supplemental && it.id.length <= 128 }.distinctBy { it.id }.take(16).forEach { window ->
            val remaining = 1 - window.utilization
            if (!window.utilization.isFinite() || remaining < -1 || remaining > 2) return@forEach
            val duration = window.durationSeconds?.takeIf { it in 60..(366L * 86400) }
            val end = window.resetsAt?.epochSecond?.takeIf { it in measured..(measured + (duration ?: 366L * 86400)) }
            val old = next[window.id]
            val last = old?.samples?.lastOrNull()
            if (last != null && measured <= last.at) return@forEach
            val same = old != null && old.cycleEnd == end && old.duration == duration &&
                old.source == quota.source && last != null && measured - last.at <= 6 * 3600 &&
                remaining <= last.remaining
            val samples = (if (same) old!!.samples else emptyList()) + PaceSample(measured, remaining)
            next[window.id] = WindowHistory(end, duration, samples.takeLast(MAX), quota.source)
        }
        return retained(next, now)
    }

    fun retained(history: Map<String, WindowHistory>, now: Long): Map<String, WindowHistory> = history.entries
        .filter { it.key.length <= 128 }
        .mapNotNull { (id, window) ->
            val samples = window.samples.filter { it.at in (now - 14 * 86400)..now &&
                it.remaining.isFinite() && it.remaining in -1.0..1.0 }.distinctBy { it.at }.sortedBy { it.at }.takeLast(MAX)
            if (samples.isEmpty()) null else id to window.copy(samples = samples)
        }.sortedByDescending { it.second.samples.last().at }.take(16).toMap()

    /** Linear estimate only. Not a forecast of future capacity; missing reset means no guide. */
    fun estimate(history: WindowHistory?, now: Long): PaceEstimate? {
        val samples = history?.samples.orEmpty()
        if (samples.size < 3 || samples.last().at < now - 6 * 3600) return null
        if (samples.any { it.at !in (now - 14 * 86400)..now || !it.remaining.isFinite() } ||
            samples.zipWithNext().any { (a, b) -> b.at - a.at !in 1..(6 * 3600) || b.remaining > a.remaining }) return null
        if (history?.cycleEnd?.let { it <= now } == true) return null
        val span = samples.last().at - samples.first().at
        if (span < 15 * 60) return null
        val slope = (samples.last().remaining - samples.first().remaining) / span
        val runsOut = if (slope < -1e-8) {
            val seconds = samples.last().remaining / -slope
            if (seconds in 0.0..(14 * 86400.0)) samples.last().at + seconds.toLong() else null
        } else null
        return PaceEstimate(samples, runsOut?.takeIf { history?.cycleEnd == null || it <= history.cycleEnd },
            history?.cycleEnd, history?.duration)
    }

    fun principal(quota: QuotaInfo?): UsageWindow? = quota?.windows
        ?.filter { !it.supplemental && it.utilization.isFinite() }
        ?.maxByOrNull { it.utilization }
}
