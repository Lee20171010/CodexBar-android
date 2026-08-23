package com.codexbar.android.core.network.claude

import kotlinx.serialization.Serializable

@Serializable
internal data class ClaudeCompanionRequest(
    val protocolVersion: Int,
    val companionId: String,
    val requestedAtEpochSeconds: Long,
    val nonce: String,
    val signature: String
)

@Serializable
internal data class ClaudeCompanionEnvelope(
    val protocolVersion: Int,
    val companionId: String,
    val requestNonce: String,
    val sentAtEpochSeconds: Long,
    val iv: String,
    val ciphertext: String
)

@Serializable
data class ClaudeCompanionSnapshot(
    val schemaVersion: Int,
    val source: String,
    val generatedAtEpochSeconds: Long,
    val cliVersion: String,
    val tier: String? = null,
    val windows: List<ClaudeCompanionWindow>
)

@Serializable
data class ClaudeCompanionWindow(
    val label: String,
    val usedFraction: Double,
    val resetsAtEpochSeconds: Long? = null
)
