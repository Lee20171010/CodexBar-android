package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageDataConfidence
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.UsageWindowSlot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToLong

/**
 * Mac card pace policy (ProviderPaceCapability.resolvedKind plus the UsagePaceText/UsageStore call
 * paths): only windows whose source slot or reset-window rule carries pace semantics get a pace.
 * No label or reset-time guessing; alert paths keep their own semantics.
 */
object PacePolicy {
    private const val SESSION_MAX_SECONDS = 18_000L          // Mac .session(maximumMinutes: 300)
    private const val WEEKLY_DEFAULT_SECONDS = 604_800L      // Mac defaultWindowMinutes 10080
    private const val MONTHLY_SENTINEL_SECONDS = 2_592_000L  // Mac monthlyWindowSentinelMinutes 43200

    fun pace(quota: QuotaInfo, window: UsageWindow, now: Instant,
        workDays: Int? = null, zone: ZoneId = ZoneId.systemDefault()): UsagePace? {
        if (window.resetsAt == null || window.supplemental || window.kind != UsageWindowKind.QUOTA) return null
        if (!window.utilization.isFinite() || window.utilization < 0 || window.utilization >= 1.0) return null // Mac remainingPercent > 0
        // Mac allowsPace: OpenCode Go alone rejects estimated payloads (allowsEstimatedUsage: false).
        if (quota.service == AiService.OPENCODE_GO && quota.dataConfidence == UsageDataConfidence.ESTIMATED) return null
        // Mac resolvedKind: the reset-window rule wins over slot lanes. Copilot matches on any reset
        // date (inferring the month only when the duration is missing); Go matches its exact 30-day sentinel.
        val resetPaced: UsageWindow? = when (quota.service) {
            AiService.COPILOT -> if (window.durationSeconds == null) inferCalendarMonth(window) else window
            AiService.OPENCODE_GO -> if (window.durationSeconds == MONTHLY_SENTINEL_SECONDS) inferCalendarMonth(window) else null
            else -> null
        }
        if (resetPaced != null) {
            // Mac resetWindowPaceDetail gate: displayableWeeklyPace = expected>=3 or eta==0.
            val pace = QuotaPace.weeklyPace(resetPaced, now, workDays, zone) ?: return null
            return pace.takeIf { it.expectedUsedPercent >= 3.0 || it.etaSeconds == 0L }
        }
        val lane = when (quota.service to window.slot) {
            AiService.CODEX to UsageWindowSlot.PRIMARY,
            AiService.CLAUDE to UsageWindowSlot.PRIMARY,
            AiService.OPENCODE_GO to UsageWindowSlot.PRIMARY ->
                if (window.durationSeconds == null || window.durationSeconds <= SESSION_MAX_SECONDS)
                    window.copy(durationSeconds = window.durationSeconds ?: SESSION_MAX_SECONDS) else null
            AiService.CODEX to UsageWindowSlot.SECONDARY ->
                // Codex alone keeps the weekly default; generic lanes must carry an explicit duration.
                window.copy(durationSeconds = window.durationSeconds ?: WEEKLY_DEFAULT_SECONDS)
            AiService.CLAUDE to UsageWindowSlot.SECONDARY,
            AiService.CLAUDE to UsageWindowSlot.TERTIARY,
            AiService.OPENCODE_GO to UsageWindowSlot.SECONDARY ->
                window.takeIf { it.durationSeconds != null }
            else -> null
        } ?: return null
        val pace = QuotaPace.weeklyPace(lane, now, workDays, zone) ?: return null
        // Session and weekly lane call paths gate expected>=3 with no eta==0 exemption.
        return pace.takeIf { it.expectedUsedPercent >= 3.0 }
    }

    /** Mac inferredMonthlyWindowMinutes: Gregorian UTC month ending at the reset date, whole minutes. */
    private fun inferCalendarMonth(window: UsageWindow): UsageWindow? {
        val resets = window.resetsAt ?: return null
        val seconds = Duration.between(resets.atZone(ZoneOffset.UTC).minusMonths(1).toInstant(), resets).seconds
        if (seconds <= 0) return null
        return window.copy(durationSeconds = (seconds / 60.0).roundToLong() * 60)
    }
}
