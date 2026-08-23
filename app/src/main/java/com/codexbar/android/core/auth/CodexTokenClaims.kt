package com.codexbar.android.core.auth

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val codexClaimsJson = Json { ignoreUnknownKeys = true }

internal fun codexTokenExpiresAt(token: String): Instant? {
    val claims = decodeJwtClaims(token) ?: return null
    val epochSeconds = (claims["exp"] as? JsonPrimitive)?.longOrNull ?: return null
    return runCatching { Instant.ofEpochSecond(epochSeconds) }.getOrNull()
}

internal fun codexAccountId(idToken: String?, accessToken: String): String? {
    return sequenceOf(idToken, accessToken)
        .filterNotNull()
        .mapNotNull(::decodeJwtClaims)
        .mapNotNull(::accountIdFromClaims)
        .firstOrNull()
}

private fun accountIdFromClaims(claims: JsonObject): String? {
    val namespace = claims[OPENAI_AUTH_NAMESPACE] as? JsonObject
    val candidates = sequenceOf(
        claims.stringOrNull("chatgpt_account_id"),
        namespace?.stringOrNull("chatgpt_account_id"),
        claims.stringOrNull("$OPENAI_AUTH_NAMESPACE.chatgpt_account_id")
    )
    return candidates.firstOrNull { candidate ->
        candidate != null &&
            candidate.length in 1..MAX_ACCOUNT_ID_LENGTH &&
            candidate.none(Char::isISOControl)
    }
}

private fun decodeJwtClaims(token: String): JsonObject? {
    val parts = token.split('.')
    if (parts.size != 3 || parts[1].length > MAX_JWT_PAYLOAD_LENGTH) return null
    return runCatching {
        val payload = String(
            Base64.getUrlDecoder().decode(parts[1]),
            StandardCharsets.UTF_8
        )
        codexClaimsJson.parseToJsonElement(payload).jsonObject
    }.getOrNull()
}

private fun JsonObject.stringOrNull(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}

private const val OPENAI_AUTH_NAMESPACE = "https://api.openai.com/auth"
private const val MAX_ACCOUNT_ID_LENGTH = 128
private const val MAX_JWT_PAYLOAD_LENGTH = 16_384
