package com.codexbar.android.core.network.codex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object CodexDto {

    @Serializable
    data class UsageResponse(
        @SerialName("plan_type") val planType: String? = null,
        @SerialName("rate_limit") val rateLimit: RateLimit? = null,
        val credits: Credits? = null,
        @SerialName("additional_rate_limits") val additionalRateLimits: JsonElement? = null
    )

    @Serializable
    data class RateLimit(
        @SerialName("primary_window") val primaryWindow: RateLimitWindow? = null,
        @SerialName("secondary_window") val secondaryWindow: RateLimitWindow? = null
    )

    @Serializable
    data class RateLimitWindow(
        @SerialName("used_percent") val usedPercent: Double = 0.0,
        @SerialName("reset_at") val resetAt: Long? = null, // Unix timestamp
        @SerialName("limit_window_seconds") val limitWindowSeconds: Long? = null
    )

    @Serializable
    data class Credits(
        @SerialName("has_credits") val hasCredits: Boolean = false,
        val unlimited: Boolean = false,
        val balance: JsonElement? = null // Can be Double or String
    )

    @Serializable
    data class ResetCreditsResponse(
        val credits: List<ResetCredit> = emptyList(),
        @SerialName("available_count") val availableCount: Int = 0
    )

    @Serializable
    data class ResetCredit(
        val status: String,
        @SerialName("expires_at") val expiresAt: String? = null
    )

    @Serializable
    data class TokenRefreshRequest(
        @SerialName("client_id") val clientId: String,
        @SerialName("grant_type") val grantType: String,
        @SerialName("refresh_token") val refreshToken: String
    )

    @Serializable
    data class TokenRefreshResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("token_type") val tokenType: String? = null
    )

    @Serializable
    data class TokenErrorResponse(
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"

    val TERMINAL_ERROR_CODES = setOf(
        "invalid_grant",
        "refresh_token_expired",
        "refresh_token_reused",
        "refresh_token_invalidated"
    )

    internal fun refreshErrorCode(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(errorBody).jsonObject
        }.getOrNull() ?: return null
        val error = root["error"]
        return when (error) {
            is JsonPrimitive -> error.contentOrNull
            is JsonObject -> (error["code"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }

    internal fun isTerminalRefreshFailure(httpCode: Int, errorBody: String): Boolean {
        if (httpCode == 401) return true
        return httpCode == 400 && refreshErrorCode(errorBody) in TERMINAL_ERROR_CODES
    }
}
