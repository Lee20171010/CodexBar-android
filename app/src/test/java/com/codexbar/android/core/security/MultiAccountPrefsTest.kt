package com.codexbar.android.core.security

import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-account layout default-once resolution and display-only connection pointers. */
class MultiAccountPrefsTest {
    @Test fun `fresh storage resolves segmented once and keeps it after accounts and restart`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        assertTrue(store.isMultiAccountSegmented())
        assertTrue("default must be persisted at init, not re-derived later",
            memory.prefs.all.containsKey("multi_account_segmented"))
        val account = AccountConnection.create(AiService.CLAUDE)
        store.saveValidatedConnection(account, Credential.ClaudeCredential("synthetic-a", "synthetic-r"))
        assertTrue(EncryptedPrefsManager(memory.prefs).isMultiAccountSegmented())
    }

    @Test fun `storage with account records before the key existed preserves stacked`() {
        val memory = MemoryPreferences()
        val account = AccountConnection.create(AiService.CODEX)
        memory.prefs.edit()
            .putString("${account.id}_connection_provider", account.service.name)
            .putString("${account.id}_connection_generation", account.generation)
            .commit()
        assertFalse(EncryptedPrefsManager(memory.prefs).isMultiAccountSegmented())
    }

    @Test fun `legacy token-only storage before the key existed preserves stacked`() {
        val memory = MemoryPreferences()
        memory.prefs.edit().putString("CODEX_access_token", "synthetic").commit()
        assertFalse(EncryptedPrefsManager(memory.prefs).isMultiAccountSegmented())
    }

    @Test fun `explicit choice is never overwritten and survives delete-all`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        store.setMultiAccountSegmented(false)
        val account = AccountConnection.create(AiService.CLAUDE)
        store.saveValidatedConnection(account, Credential.ClaudeCredential("synthetic-a", "synthetic-r"))
        store.deleteConnection(account)
        assertFalse("delete-all keeps the stored global preference like other globals",
            EncryptedPrefsManager(memory.prefs).isMultiAccountSegmented())
        EncryptedPrefsManager(memory.prefs).setMultiAccountSegmented(true)
        assertTrue(EncryptedPrefsManager(memory.prefs).isMultiAccountSegmented())
    }

    @Test fun `displayed pointer round-trips restart and rejects stale selections`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        val first = AccountConnection.create(AiService.CLAUDE)
        val second = AccountConnection.create(AiService.CLAUDE)
        store.saveValidatedConnection(first, Credential.ClaudeCredential("synthetic-a", "synthetic-r"))
        store.saveValidatedConnection(second, Credential.ClaudeCredential("synthetic-b", "synthetic-r"))
        store.setDisplayedConnection(second)
        assertEquals(mapOf("CLAUDE" to second.id), store.displayedConnections.value)
        assertEquals(mapOf("CLAUDE" to second.id), EncryptedPrefsManager(memory.prefs).displayedConnections.value)
        assertFalse("a replaced generation must be rejected", store.setDisplayedConnection(first.reconnect()))
        store.deleteConnection(second)
        assertFalse("a deleted account must be rejected", store.setDisplayedConnection(second))
        assertEquals("a stale stored pointer is kept and falls back at display time",
            mapOf("CLAUDE" to second.id), store.displayedConnections.value)
    }
}
