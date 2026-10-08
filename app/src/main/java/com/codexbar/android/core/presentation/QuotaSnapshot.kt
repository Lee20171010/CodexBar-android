@file:kotlinx.serialization.UseSerializers(com.codexbar.android.core.domain.model.InstantSerializer::class)

package com.codexbar.android.core.presentation

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.*
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
enum class QuotaFailure { NETWORK, RATE_LIMITED, SERVICE, PARSE, AUTH, AUTH_PENDING, MISSING }
enum class Freshness { CURRENT, STALE, UNAVAILABLE, RECONNECT }

/** Measurement time belongs to the provider reading, never to a render or failed attempt. */
@Serializable
data class QuotaSnapshot(
    val quota: QuotaInfo? = null,
    val attemptedAt: Instant? = null,
    val failure: QuotaFailure? = null
) {
    fun retained(now: Instant): QuotaSnapshot = if (quota != null &&
        (quota.fetchedAt.isAfter(now.plusSeconds(300)) || quota.fetchedAt.isBefore(now.minusSeconds(RETENTION_SECONDS))))
        copy(quota = null) else this

    fun freshness(now: Instant = Instant.now()): Freshness = when {
        failure == QuotaFailure.AUTH || failure == QuotaFailure.MISSING -> Freshness.RECONNECT
        retained(now).quota == null -> Freshness.UNAVAILABLE
        failure != null || quota!!.source == "legacy" || quota.fetchedAt.isAfter(now) ||
            Duration.between(quota.fetchedAt, now).seconds > 3600 -> Freshness.STALE
        else -> Freshness.CURRENT
    }

    fun after(result: Result<QuotaInfo, AppError>, now: Instant): QuotaSnapshot = when (result) {
        is Result.Success -> QuotaSnapshot(result.value, now)
        is Result.Failure -> {
            val kind = when (result.error) {
                is AppError.AuthError -> if (result.error.isTerminal) QuotaFailure.AUTH else QuotaFailure.AUTH_PENDING
                is AppError.CredentialNotFound -> QuotaFailure.MISSING
                is AppError.NetworkError -> QuotaFailure.NETWORK
                is AppError.RateLimited -> QuotaFailure.RATE_LIMITED
                is AppError.ServiceUnavailable -> QuotaFailure.SERVICE
                is AppError.ParseError -> QuotaFailure.PARSE
            }
            QuotaSnapshot(if (kind in listOf(QuotaFailure.AUTH, QuotaFailure.AUTH_PENDING, QuotaFailure.MISSING)) null else retained(now).quota, now, kind)
        }
    }

    companion object { const val RETENTION_SECONDS = 7 * 24 * 60 * 60L }
}

object QuotaPresentation {
    fun reset(context: Context, at: Instant?, now: Instant = Instant.now()): String? {
        if (at == null) return null
        val seconds = Duration.between(now, at).seconds
        return when {
            seconds <= 0 -> context.getString(R.string.reset_due)
            seconds >= 86400 -> context.getString(R.string.reset_days, seconds / 86400, (seconds / 3600) % 24)
            seconds >= 3600 -> context.getString(R.string.reset_hours, seconds / 3600, (seconds / 60) % 60)
            else -> context.getString(R.string.reset_minutes, seconds / 60)
        }
    }
    fun remainingPercent(utilization: Double): Int = ((1 - utilization).coerceIn(0.0, 1.0) * 100).roundToInt()
    fun principal(quota: QuotaInfo): List<UsageWindow> = quota.windows.filterNot { it.supplemental }
    fun age(context: Context, at: Instant?, now: Instant = Instant.now()): String {
        if (at == null) return context.getString(R.string.not_measured)
        val seconds = Duration.between(at, now).seconds
        return when {
            seconds < 0 -> context.getString(R.string.clock_changed)
            seconds < 60 -> context.getString(R.string.measured_now)
            seconds < 3600 -> context.getString(R.string.measured_minutes, seconds / 60)
            seconds < 86400 -> context.getString(R.string.measured_hours, seconds / 3600)
            else -> context.getString(R.string.measured_days, seconds / 86400)
        }
    }
    fun status(context: Context, snapshot: QuotaSnapshot, now: Instant = Instant.now()): String = when (snapshot.freshness(now)) {
        Freshness.RECONNECT -> context.getString(R.string.reconnect_required)
        Freshness.UNAVAILABLE -> context.getString(R.string.quota_unavailable)
        Freshness.STALE -> context.getString(R.string.stale_measurement, age(context, snapshot.quota?.fetchedAt, now))
        Freshness.CURRENT -> age(context, snapshot.quota?.fetchedAt, now)
    }
    fun summary(context: Context, snapshot: QuotaSnapshot, now: Instant = Instant.now()): String {
        val quota = snapshot.retained(now).quota ?: return status(context, snapshot, now)
        val window = principal(quota).maxByOrNull { it.utilization }
        val value = window?.let { "${it.label} ${context.getString(R.string.percent_left, remainingPercent(it.utilization))}" }
            ?: quota.money?.balanceText() ?: context.getString(R.string.quota_unavailable)
        return "$value · ${status(context, snapshot, now)}"
    }
}
