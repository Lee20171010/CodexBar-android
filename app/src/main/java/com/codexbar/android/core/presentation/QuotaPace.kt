package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
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

enum class PaceStage { ON_TRACK, SLIGHTLY_AHEAD, AHEAD, FAR_AHEAD, SLIGHTLY_BEHIND, BEHIND, FAR_BEHIND }

/** Mac UsagePace equivalent: actual vs expected usage at this point in the window. */
data class UsagePace(val stage: PaceStage, val deltaPercent: Double, val expectedUsedPercent: Double,
    val actualUsedPercent: Double, val etaSeconds: Long?, val willLastToReset: Boolean)

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

    /**
     * Mac UsagePace.weekly port: expected vs actual usage for a window with a known
     * duration. workDays (2..6) makes weekly windows count only the first N ISO weekdays.
     */
    fun weeklyPace(window: UsageWindow, now: Instant, workDays: Int? = null,
        zone: ZoneId = ZoneId.systemDefault()): UsagePace? {
        val resets = window.resetsAt ?: return null
        val minutes = (window.durationSeconds ?: 10080L).floorDiv(60)
        if (minutes <= 0) return null
        val duration = minutes * 60
        val timeUntilReset = Duration.between(now, resets).seconds
        if (timeUntilReset <= 0 || timeUntilReset > duration) return null
        val elapsed = duration - timeUntilReset
        val workday = if (workDays != null && workDays in 2..6 && minutes == 10080L)
            workdayProgress(now, duration, resets, workDays, zone) else null
        val expected = (workday?.expectedUsedPercent
            ?: ((elapsed / duration.toDouble()) * 100)).coerceIn(0.0, 100.0)
        val actual = (window.utilization * 100).coerceIn(0.0, 100.0)
        if (elapsed == 0L && actual > 0) return null
        val delta = actual - expected
        val paceElapsed = workday?.elapsedSeconds ?: elapsed.toDouble()
        val effectiveRemaining = workday?.remainingSeconds ?: timeUntilReset.toDouble()
        var etaSeconds: Long? = null
        var willLast = false
        when {
            actual >= 100.0 -> etaSeconds = 0
            paceElapsed > 0 && actual > 0 -> {
                val candidate = (100 - actual) / (actual / paceElapsed)
                if (candidate >= effectiveRemaining) willLast = true
                else etaSeconds = if (workday != null && workDays != null)
                    wallClockInterval(now, resets, candidate, workDays, zone) else candidate.toLong()
            }
            paceElapsed > 0 -> willLast = true
        }
        return UsagePace(stageOf(delta), delta, expected, actual, etaSeconds, willLast)
    }

    private class WorkdayProgress(val totalSeconds: Double, val elapsedSeconds: Double, val remainingSeconds: Double) {
        val expectedUsedPercent get() = ((elapsedSeconds / totalSeconds) * 100).coerceIn(0.0, 100.0)
    }

    /** Splits the weekly window at local day boundaries so reset offsets do not shift weekday classification. */
    private fun workdayProgress(now: Instant, duration: Long, resetsAt: Instant, workDays: Int,
        zone: ZoneId): WorkdayProgress? {
        var cursor = resetsAt.atZone(zone).minusSeconds(duration).toInstant()
        var total = 0.0; var elapsed = 0.0; var remaining = 0.0
        while (cursor.isBefore(resetsAt)) {
            val nextDay = cursor.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            if (!nextDay.isAfter(cursor)) return null
            val sliceEnd = if (nextDay.isBefore(resetsAt)) nextDay else resetsAt
            if (isWorkday(cursor, zone, workDays)) {
                total += Duration.between(cursor, sliceEnd).seconds
                if (now.isAfter(cursor)) elapsed += minOf(now.epochSecond, sliceEnd.epochSecond) - cursor.epochSecond
                if (now.isBefore(sliceEnd)) remaining += sliceEnd.epochSecond - maxOf(now.epochSecond, cursor.epochSecond)
            }
            cursor = sliceEnd
        }
        return if (total > 0) WorkdayProgress(total, elapsed, remaining) else null
    }

    private fun wallClockInterval(now: Instant, resetsAt: Instant, required: Double, workDays: Int,
        zone: ZoneId): Long? {
        if (required <= 0) return 0
        var remaining = required
        var cursor = now
        while (cursor.isBefore(resetsAt)) {
            val nextDay = cursor.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            if (!nextDay.isAfter(cursor)) return null
            val sliceEnd = if (nextDay.isBefore(resetsAt)) nextDay else resetsAt
            if (isWorkday(cursor, zone, workDays)) {
                val available = Duration.between(cursor, sliceEnd).seconds
                if (remaining <= available) return Duration.between(now, cursor.plusSeconds(remaining.toLong())).seconds
                remaining -= available
            }
            cursor = sliceEnd
        }
        return null
    }

    private fun isWorkday(at: Instant, zone: ZoneId, workDays: Int): Boolean =
        at.atZone(zone).dayOfWeek.value <= workDays // ISO weekday: Monday = 1

    private fun stageOf(delta: Double): PaceStage = when {
        abs(delta) <= 2.0 -> PaceStage.ON_TRACK
        abs(delta) <= 6.0 -> if (delta >= 0) PaceStage.SLIGHTLY_AHEAD else PaceStage.SLIGHTLY_BEHIND
        abs(delta) <= 12.0 -> if (delta >= 0) PaceStage.AHEAD else PaceStage.BEHIND
        else -> if (delta >= 0) PaceStage.FAR_AHEAD else PaceStage.FAR_BEHIND
    }
}
