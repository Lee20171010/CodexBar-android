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
    val source: String = "kotlin-api",
    val credits: ReportedCredits? = null,
    val resetInventory: ResetInventory? = null
)

@kotlinx.serialization.Serializable
data class ReportedCredits(val balance: Double?, val workspace: Boolean, val cap: CreditCap?, val fetchedAt: Instant)

@kotlinx.serialization.Serializable
data class CreditCap(val used: Double, val limit: Double, val remaining: Double, val resetsAt: Instant?, val fetchedAt: Instant)

@kotlinx.serialization.Serializable
data class ResetInventory(val availableCount: Int, val items: List<ResetCredit>, val fetchedAt: Instant)

@kotlinx.serialization.Serializable
data class ResetCredit(val type: String, val status: String, val expiresAt: Instant?)

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
