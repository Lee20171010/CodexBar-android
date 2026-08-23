package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.ProviderCategory
import com.codexbar.android.core.domain.model.providerMetadata
import com.codexbar.android.core.security.ConnectionHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDirectoryTest {
    private val states = AiService.entries.associateWith { ServiceCredentialState() } +
        (AiService.CURSOR to ServiceCredentialState(
            isConnected = true,
            connectionHealth = ConnectionHealth.CONNECTED
        )) +
        (AiService.CLAUDE to ServiceCredentialState(isConnected = true))

    @Test
    fun `connected providers are pinned before recommended providers`() {
        val results = filterProviders(
            "",
            ProviderConnectionFilter.ALL,
            ProviderCategoryFilter.ALL,
            states
        )

        assertEquals(AiService.CURSOR, results.first())
    }

    @Test
    fun `search matches provider aliases case insensitively`() {
        assertEquals(
            listOf(AiService.ZAI),
            filterProviders(
                "GLM",
                ProviderConnectionFilter.ALL,
                ProviderCategoryFilter.ALL,
                states
            )
        )
        assertEquals(
            listOf(AiService.CODEX),
            filterProviders(
                "openai",
                ProviderConnectionFilter.ALL,
                ProviderCategoryFilter.ALL,
                states
            )
        )
    }

    @Test
    fun `connection filters partition the provider list`() {
        val connected = filterProviders(
            "",
            ProviderConnectionFilter.CONNECTED,
            ProviderCategoryFilter.ALL,
            states
        )
        val disconnected = filterProviders(
            "",
            ProviderConnectionFilter.NOT_CONNECTED,
            ProviderCategoryFilter.ALL,
            states
        )

        assertEquals(listOf(AiService.CURSOR), connected)
        assertTrue(AiService.CLAUDE in disconnected)
        assertTrue(AiService.CURSOR !in disconnected)
        assertEquals(AiService.entries.size, connected.size + disconnected.size)
    }

    @Test
    fun `category filter narrows the directory without changing connection state`() {
        val routers = filterProviders(
            "",
            ProviderConnectionFilter.ALL,
            ProviderCategoryFilter.ROUTER,
            states
        )

        assertEquals(
            AiService.entries.filter { it.providerMetadata.category == ProviderCategory.ROUTER },
            routers
        )
        assertTrue(states.getValue(AiService.CURSOR).isConnected)
    }

    @Test
    fun `saved credentials are not called connected until health is verified`() {
        assertTrue(states.getValue(AiService.CLAUDE).isConnected)
        assertTrue(!states.getValue(AiService.CLAUDE).isVerifiedConnected)
    }
}
