package com.codexbar.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    @Test
    fun `catalog covers every service with a stable guide anchor`() {
        val metadata = AiService.entries.map { ProviderCatalog.metadataFor(it) }

        assertEquals(AiService.entries.size, metadata.size)
        assertEquals(metadata.size, metadata.map { it.guideAnchor }.toSet().size)
    }

    @Test
    fun `auth mode determines stored provider secret kind`() {
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.ZAI.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.KIMI.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.ELEVENLABS.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.OPENROUTER.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.SYNTHETIC.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.CHUTES.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.DEEPSEEK.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.VENICE.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.MOONSHOT.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.CLINEPASS.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.IBM_BOB.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.FIREWORKS.providerMetadata.secretKind
        )
        assertEquals(true, AiService.FIREWORKS.providerMetadata.requiresAccountReference)
        assertEquals(
            ProviderSecretKind.COOKIE_HEADER,
            AiService.CURSOR.providerMetadata.secretKind
        )
        assertNull(AiService.CODEX.providerMetadata.secretKind)
    }

    @Test
    fun `OpenCode Go uses one internal identity and cookie-auth catalog entry`() {
        val service = AiService.entries.single { it.displayName == "OpenCode Go" }
        assertEquals("OPENCODE_GO", service.name)
        assertEquals(ProviderAuthMode.SESSION_COOKIE, service.providerMetadata.authMode)
        assertEquals(ProviderSecretKind.COOKIE_HEADER, service.providerMetadata.secretKind)
        assertEquals("opencode-go", service.providerMetadata.guideAnchor)
        assertTrue("opencode" in service.providerMetadata.aliases)
        assertTrue("open code" in service.providerMetadata.aliases)
        assertTrue("opencode go" in service.providerMetadata.aliases)
        assertTrue("open code go" in service.providerMetadata.aliases)
    }
}
