package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.UsageWindowSlot
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Mac LimitResetDetectorState plus its notification receipt, persisted per lane.
 * Pre-policy rows (no `migrationFloorUntil`) are migrated conservatively (baseline from usage,
 * no fabricated boundary or receipt); the old cooldown survives only as an expiring
 * notification-suppression floor and never alters detector transitions.
 */
@Serializable
data class RecoveryReading(
    val at: Long,
    val used: Double,
    val source: String,
    val notifiedUntil: Long = 0,
    val v: Int = 0,
    val wasAbove: Boolean = false,
    val wasAboveCandidate: Boolean = false,
    val boundary: Long? = null,
    val plan: String? = null,
    val pendingCandidate: Boolean = false,
    val candidateAt: Long = 0,
    val postedBoundary: Long? = null,
    val receiptPresent: Boolean = false,
    val receiptBoundary: Long? = null,
    val lastNotifiedBoundary: Long? = null,
    val recoveryCount: Int? = null,
    val migrationFloorUntil: Long = 0
)

/** Mac UsageStore limit-reset celebration detector (LimitResetCelebration/LimitResetBoundary/LimitResetNotifications). */
object QuotaRecovery {
    private const val ABOVE = 0.01 // Mac wasAboveThreshold: used > 1%
    private const val CANDIDATE = 0.25 // Mac Codex candidate baseline: used > 25%
    private const val CODEX_LOW = 0.05 // Mac Codex weekly post threshold: used <= 5%
    private const val DEPLETED = 0.999999 // Mac SessionQuotaNotificationLogic.isDepleted re-arm
    private const val CONFIRM_MIN = 60L
    private const val CONFIRM_MAX = 1800L
    private const val GENERIC_WEEKLY_SECONDS = 604_800L // Mac weeklyWindowMinutes 10080, exact match
    private const val MAX_LANES = 32 // bounded carry-over of lane state across temporary absence

    private enum class Lane { SESSION, WEEKLY }

    fun observe(previous: Map<String, RecoveryReading>, quota: QuotaInfo, now: Long, notifyEnabled: Boolean = true):
        Pair<Map<String, RecoveryReading>, List<UsageWindow>> {
        // Android adaptations: stale-cache and owner guards have no Mac equivalent (single live store).
        val measured = quota.fetchedAt.epochSecond
        if (measured !in (now - 300)..now || quota.source == "legacy") return previous to emptyList()
        val windows = quota.windows.filter {
            it.kind == UsageWindowKind.QUOTA && !it.supplemental && it.id.length <= 128 &&
                it.utilization.isFinite() && it.utilization >= 0
        }.distinctBy { it.id }.take(32)
        val next = LinkedHashMap<String, RecoveryReading>()
        val recovered = mutableListOf<UsageWindow>()
        sessionLane(quota, windows)?.let { window ->
            val key = "session:${window.id}"
            val (reading, notice) = transition(Lane.SESSION, quota, window, seed(previous, key), measured, now, notifyEnabled)
            next[key] = reading
            if (notice) recovered += window
        }
        weeklyLane(quota, windows)?.let { window ->
            val key = "weekly:${window.id}"
            val (reading, notice) = transition(Lane.WEEKLY, quota, window, seed(previous, key), measured, now, notifyEnabled)
            next[key] = reading
            if (notice) recovered += window
        }
        // Absent lanes keep their state (receipts included) so returning metadata cannot re-alert;
        // only the bounded cap evicts, and account removal drops the whole record upstream.
        previous.forEach { (key, reading) -> if (':' in key && key !in next) next[key] = reading }
        while (next.size > MAX_LANES) {
            val oldest = next.entries.minByOrNull { it.value.at } ?: break
            next.remove(oldest.key)
        }
        return next to recovered
    }

    /**
     * Mac session lane: Codex uses the owner-scoped projection lane (PRIMARY); other providers need a
     * PRIMARY with an explicit duration of 1..360 minutes (isSemanticSessionResetWindow); Copilot may
     * fall back to SECONDARY regardless of duration (copilotSecondaryFallback).
     */
    private fun sessionLane(quota: QuotaInfo, windows: List<UsageWindow>): UsageWindow? {
        val primary = windows.firstOrNull { it.slot == UsageWindowSlot.PRIMARY }
        return when (quota.service) {
            AiService.CODEX -> primary
            AiService.COPILOT -> primary?.takeIf { it.durationSeconds?.let { d -> d in 1..21_600 } == true }
                ?: windows.firstOrNull { it.slot == UsageWindowSlot.SECONDARY }
            else -> primary?.takeIf { it.durationSeconds?.let { d -> d in 1..21_600 } == true }
        }
    }

    /**
     * Mac weekly series mapping (PlanUtilization provider switch): Codex/Claude/OpenCodeGo name their
     * SECONDARY lane weekly and require an explicit duration; every other provider resolves the generic
     * session-equivalent pair, which matches exactly 10080 minutes and is ambiguous when several match.
     */
    private fun weeklyLane(quota: QuotaInfo, windows: List<UsageWindow>): UsageWindow? = when (quota.service) {
        AiService.CODEX, AiService.CLAUDE, AiService.OPENCODE_GO -> windows.firstOrNull {
            it.slot == UsageWindowSlot.SECONDARY && it.durationSeconds?.let { d -> d > 0 } == true
        }
        else -> windows.filter { it.durationSeconds == GENERIC_WEEKLY_SECONDS }.singleOrNull()
    }

    /** Lane state, or the legacy per-window record (bare key) migrated conservatively. */
    private fun seed(previous: Map<String, RecoveryReading>, key: String): RecoveryReading? {
        previous[key]?.let { return it }
        val legacy = previous[key.substringAfter(':')]?.takeIf { it.v == 0 } ?: return null
        return RecoveryReading(
            at = legacy.at, used = legacy.used, source = legacy.source,
            notifiedUntil = legacy.notifiedUntil, v = 1,
            wasAbove = legacy.used > ABOVE, wasAboveCandidate = legacy.used > CANDIDATE,
            migrationFloorUntil = legacy.notifiedUntil
        )
    }

    /** Faithful translation of limitResetDetectorTransition + prepareLimitResetNotification (reset notice only). */
    private fun transition(
        lane: Lane, quota: QuotaInfo, window: UsageWindow, old: RecoveryReading?,
        measured: Long, now: Long, notifyEnabled: Boolean
    ): Pair<RecoveryReading, Boolean> {
        val used = window.utilization
        val boundary = window.resetsAt?.epochSecond
        if (old == null) {
            return RecoveryReading(
                at = measured, used = used, source = quota.source, v = 1,
                wasAbove = used > ABOVE, wasAboveCandidate = used > CANDIDATE,
                boundary = boundary, plan = planOf(lane, quota)
            ) to false
        }
        if (measured <= old.at) return old to false // duplicate or out-of-order reading

        val isCodexWeekly = lane == Lane.WEEKLY && quota.service == AiService.CODEX
        val isClaudeWeekly = lane == Lane.WEEKLY && quota.service == AiService.CLAUDE
        val wasAbove = used > ABOVE
        val wasAboveCandidate = used > CANDIDATE
        val plan = planOf(lane, quota)
        val planChanged = isCodexWeekly && old.plan != null && old.plan != plan
        val planMatches = isCodexWeekly && plan != null && old.plan == plan
        // Mac: source change only suppresses the session lane; weekly observations carry no source.
        val sourceChanged = lane == Lane.SESSION && old.source.isNotEmpty() && old.source != quota.source
        val pendingCount = if (isClaudeWeekly) old.recoveryCount else null
        val claudePending = pendingCount != null

        val boundaryAllowsPost = when {
            lane == Lane.SESSION -> boundaryAdvanced(old.boundary, boundary, requiresPrevious = false)
            isCodexWeekly -> boundaryAdvanced(old.boundary, boundary, requiresPrevious = true)
            else -> true // non-Codex weekly has no boundary gate
        }
        val crossedBelow = !sourceChanged && old.wasAbove && !wasAbove

        var shouldPost: Boolean
        var pendingCandidate: Boolean
        var candidateAt: Long
        var expired = false
        if (isCodexWeekly) {
            val boundaryMatchesPrev = boundarySame(old.boundary, boundary)
            val alreadyPosted = boundarySame(old.postedBoundary, boundary)
            val pendingAge = measured - old.candidateAt
            // Mac :454-462 — the compatibility check itself expires the candidate beyond 1800s.
            val pendingCompatible = old.pendingCandidate && planMatches && boundaryMatchesPrev &&
                !alreadyPosted && used <= CODEX_LOW && boundary != null && measured < boundary &&
                pendingAge in 0..CONFIRM_MAX
            val confirming = pendingCompatible && pendingAge >= CONFIRM_MIN
            val retaining = pendingCompatible && !confirming
            expired = old.pendingCandidate && !confirming && !retaining && !boundaryAllowsPost
            val crossedIntoCandidate = !sourceChanged && old.wasAboveCandidate && used <= CODEX_LOW
            val startCandidate = !old.pendingCandidate && planMatches && crossedIntoCandidate &&
                !boundaryAllowsPost && boundaryMatchesPrev && !alreadyPosted &&
                boundary != null && measured < boundary
            val crossedIntoAdvanced = !sourceChanged && old.wasAbove && used <= CODEX_LOW
            shouldPost = !planChanged && ((crossedIntoAdvanced && boundaryAllowsPost) || confirming)
            pendingCandidate = startCandidate || retaining
            candidateAt = if (startCandidate) measured else if (retaining) old.candidateAt else 0
        } else {
            shouldPost = crossedBelow && boundaryAllowsPost && !claudePending
            pendingCandidate = false
            candidateAt = 0
        }
        // Guarded crossing keeps the pre-crossing baseline (and Codex candidate baseline) instead of erasing it.
        val preserveBaseline = !sourceChanged && !boundaryAllowsPost &&
            (if (isCodexWeekly) crossedBelow && !planChanged && !expired else crossedBelow)

        val recoveryConfirmed = pendingCount != null && used > ABOVE && pendingCount + 1 >= 2
        val recoveryCount = when {
            pendingCount == null -> if (shouldPost && isClaudeWeekly) 0 else null
            recoveryConfirmed -> null
            else -> if (used > ABOVE) pendingCount + 1 else 0
        }
        val nextWasAbove = when {
            shouldPost -> false
            claudePending -> recoveryConfirmed
            expired || planChanged -> if (isCodexWeekly) used > CANDIDATE else used > ABOVE
            preserveBaseline -> true
            else -> wasAbove
        }
        val nextWasAboveCandidate = when {
            shouldPost -> false
            isCodexWeekly && preserveBaseline -> old.wasAboveCandidate
            else -> wasAboveCandidate
        }
        val preserveBoundary = !sourceChanged && !boundaryAllowsPost &&
            (lane == Lane.SESSION || old.boundary != null)

        // Notification layer (LimitResetNotifications.swift). Receipt rearm and late-metadata
        // resolution run before the enabled check (Mac :38-68); only a fresh reset notice is
        // gated by the toggle (Mac :72) or the migration floor.
        var receiptPresent = old.receiptPresent
        var receiptBoundary = old.receiptBoundary
        var lastNotified = old.lastNotifiedBoundary
        var notice = false
        // Migration floor: the legacy cooldown may only suppress notices until it expires;
        // the detector transition itself is never gated by it.
        val floorActive = old.migrationFloorUntil != 0L && now < old.migrationFloorUntil
        val newUsageCycle = nextWasAbove && !old.wasAbove
        if (newUsageCycle || used >= DEPLETED) {
            receiptPresent = false
            receiptBoundary = null
        }
        if (boundary != null) {
            val advanced = boundaryAdvanced(lastNotified, boundary)
            if (receiptPresent && receiptBoundary == null) {
                // Late reset metadata resolves the announced cycle's receipt, even while disabled.
                if (advanced) lastNotified = boundary
                receiptBoundary = boundary
            } else if (!advanced) {
                // Same-boundary coverage guard (Mac :61-63): a confirmed cycle reserves its
                // receipt even when emission is gated by the toggle or the floor.
                if (shouldPost) { receiptPresent = true; receiptBoundary = boundary }
            } else if (shouldPost && notifyEnabled && !floorActive) {
                notice = true
            } else if (shouldPost && notifyEnabled && floorActive) {
                receiptPresent = true; receiptBoundary = boundary // the floor consumes the cycle
            }
        } else if (shouldPost && !receiptPresent) {
            if (notifyEnabled && !floorActive) notice = true
            else if (notifyEnabled && floorActive) receiptPresent = true // nil-boundary floor consumption
        }
        if (notice) {
            // Reserved before persistence: retries and restarts cannot announce this boundary twice.
            receiptPresent = true
            receiptBoundary = boundary
            if (boundary != null) lastNotified = maxOf(lastNotified ?: boundary, boundary)
        }

        return RecoveryReading(
            at = measured, used = used, source = quota.source,
            notifiedUntil = old.notifiedUntil, v = 1,
            wasAbove = nextWasAbove, wasAboveCandidate = nextWasAboveCandidate,
            boundary = if (preserveBoundary) old.boundary else boundary,
            plan = if (isCodexWeekly) plan else null,
            pendingCandidate = pendingCandidate, candidateAt = candidateAt,
            postedBoundary = when {
                planChanged -> null
                shouldPost -> boundary
                else -> old.postedBoundary
            },
            receiptPresent = receiptPresent, receiptBoundary = receiptBoundary,
            lastNotifiedBoundary = lastNotified, recoveryCount = recoveryCount,
            migrationFloorUntil = if (now >= old.migrationFloorUntil) 0L else old.migrationFloorUntil
        ) to notice
    }

    /** Mac plan: Codex weekly only, login method lowercased/trimmed. */
    private fun planOf(lane: Lane, quota: QuotaInfo): String? =
        if (lane == Lane.WEEKLY && quota.service == AiService.CODEX)
            quota.tier?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } else null

    /** Mac limitResetBoundaryAdvanced: first boundary counts unless a previous one is required. */
    private fun boundaryAdvanced(previous: Long?, current: Long?, requiresPrevious: Boolean = false): Boolean {
        if (previous == null) return !requiresPrevious
        if (current == null) return false
        return current > previous && current - previous >= 120
    }

    /** Mac boundary equivalence |Δ| < 1s; epoch-second granularity makes this exact equality. */
    private fun boundarySame(left: Long?, right: Long?): Boolean =
        left != null && right != null && abs(left - right) < 1
}
