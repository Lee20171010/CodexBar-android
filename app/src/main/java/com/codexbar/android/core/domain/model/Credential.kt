package com.codexbar.android.core.domain.model

import java.time.Instant

sealed class Credential {
    abstract val accessToken: String
    abstract val refreshToken: String?

    data class ClaudeCredential(
        override val accessToken: String,
        override val refreshToken: String?,
        val expiresAt: Instant? = null,
        val scopes: String? = null,
        val rateLimitTier: String? = null
    ) : Credential()

    data class CodexCredential(
        override val accessToken: String,
        override val refreshToken: String,
        val accountId: String? = null,
        val expiresAt: Instant? = null
    ) : Credential()

    /**
     * A local companion pairing. The shared key authenticates encrypted LAN snapshots only;
     * it is never a Google access token and cannot be used to access a Google account.
     */
    data class GeminiCompanionCredential(
        val host: String,
        val port: Int,
        val companionId: String,
        val sharedKeyBase64Url: String
    ) : Credential() {
        override val accessToken: String = sharedKeyBase64Url
        override val refreshToken: String? = null
    }

    data class CopilotCredential(
        override val accessToken: String
    ) : Credential() {
        override val refreshToken: String? = null
    }

    data class ProviderSecretCredential(
        val service: AiService,
        val kind: ProviderSecretKind,
        override val accessToken: String,
        val accountReference: String? = null
    ) : Credential() {
        override val refreshToken: String? = null
    }
}

enum class ProviderSecretKind {
    API_KEY,
    COOKIE_HEADER
}
