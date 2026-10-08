@file:kotlinx.serialization.UseSerializers(InstantSerializer::class)

package com.codexbar.android.core.domain.model

import java.time.Instant

@kotlinx.serialization.Serializable
data class QuotaInfo(
    val service: AiService,
    val windows: List<UsageWindow>,
    val extraUsage: ExtraUsage?,
    val tier: String? = null,
    val fetchedAt: Instant,
    val money: ReportedMoney? = null,
    val source: String = "kotlin-api"
)

/** Reported monetary counters, independent of quota windows. Null is unknown, not zero. */
@kotlinx.serialization.Serializable
data class ReportedMoney(
    val balance: Double?,
    val spent: Double?,
    val currency: String,
    val period: String?,
    val fetchedAt: Instant
)

@kotlinx.serialization.Serializable
data class UsageWindow(
    val label: String,
    val utilization: Double, // 0.0 ~ 1.0
    val resetsAt: Instant?,
    val id: String = label,
    val kind: UsageWindowKind = UsageWindowKind.QUOTA,
    val durationSeconds: Long? = null,
    val supplemental: Boolean = false
)

@kotlinx.serialization.Serializable
enum class UsageWindowKind { QUOTA, BUDGET }

@kotlinx.serialization.Serializable
data class ExtraUsage(
    val isEnabled: Boolean,
    val monthlyLimit: Double,
    val usedCredits: Double,
    val utilization: Double,
    val currency: String
)
