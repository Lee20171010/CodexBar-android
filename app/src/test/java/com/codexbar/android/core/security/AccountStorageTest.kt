package com.codexbar.android.core.security

import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AccountStorageTest {
    @Test fun `legacy adoption is idempotent and rollback preserves original keys`() {
        val memory = MemoryPreferences()
        memory.values.putAll(mapOf(
            "CODEX_access_token" to "synthetic-access",
            "CODEX_refresh_token" to "synthetic-refresh",
            "CODEX_account_id" to "synthetic-owner",
            "CODEX_week_resets_at" to 2000L,
            "refresh_interval_minutes" to 15L,
            "notifications_enabled" to false
        ))
        val original = memory.values.toMap()
        val store = EncryptedPrefsManager(memory.prefs)
        val legacy = store.loadConnections().single()
        assertEquals("CODEX", legacy.id)
        assertEquals(AiService.CODEX, legacy.service)
        assertEquals(original, memory.values.filterKeys(original::containsKey))
        assertEquals(legacy, store.loadConnections().single())
        val restarted = EncryptedPrefsManager(memory.prefs)
        assertEquals(legacy, restarted.loadConnections().single())
        assertEquals(store.loadCredential(AiService.CODEX), restarted.loadCredential(legacy))
        assertEquals(Instant.ofEpochSecond(2000), restarted.loadResetTimes(legacy)["week"])
        assertEquals(15L, restarted.getRefreshInterval())
        assertFalse(restarted.isNotificationsEnabled())
    }

    @Test fun `same-provider accounts isolate credentials receipts rename and deletion`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        val first = AccountConnection.create(AiService.OPENCODE_GO, "Personal")
        val second = AccountConnection.create(AiService.OPENCODE_GO, "Work")
        assertNotEquals(first.id, second.id)
        assertTrue(store.saveValidatedConnection(first, key("synthetic-a")))
        assertTrue(store.saveValidatedConnection(second, key("synthetic-b")))
        store.saveResetTimes(first, listOf("week" to Instant.ofEpochSecond(10)))
        store.saveResetTimes(second, listOf("week" to Instant.ofEpochSecond(20)))
        store.setRefreshInterval(60)
        store.setNotificationsEnabled(false)
        assertTrue(store.renameConnection(first, "Renamed"))
        val restarted = EncryptedPrefsManager(memory.prefs)
        val renamed = restarted.loadConnections().first { it.id == first.id }
        assertEquals("Renamed", renamed.name)
        assertEquals(first.generation, renamed.generation)
        assertEquals("synthetic-a", restarted.loadCredential(renamed)?.accessToken)
        assertTrue(restarted.deleteConnection(first))
        assertNull(restarted.loadCredential(first))
        assertEquals(second, restarted.loadConnections().single())
        assertEquals("synthetic-b", restarted.loadCredential(second)?.accessToken)
        assertEquals(Instant.ofEpochSecond(20), restarted.loadResetTimes(second)["week"])
        assertTrue(memory.values.keys.none { it.startsWith("${first.id}_") })
        restarted.deleteAllCredentials()
        assertTrue(restarted.loadConnections().isEmpty())
        assertEquals(60L, restarted.getRefreshInterval())
        assertFalse(restarted.isNotificationsEnabled())
    }

    @Test fun `draft and provider mismatch cannot overwrite an account`() {
        val store = EncryptedPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        val draft = AccountConnection.create(AiService.CODEX)
        assertTrue(store.saveValidatedConnection(account, key("synthetic-owner")))
        assertEquals(listOf(account), store.loadConnections())
        assertThrows(IllegalArgumentException::class.java) {
            store.saveValidatedConnection(draft, key("synthetic-wrong-provider"))
        }
        assertFalse(store.saveValidatedConnection(account, key("synthetic-overwrite")))
        assertEquals(listOf(account), store.loadConnections())
        assertEquals("synthetic-owner", store.loadCredential(account)?.accessToken)
        assertNull(store.loadCredential(draft))
    }

    @Test fun `reconnect and deletion reject late credentials receipts and publication`() {
        val store = EncryptedPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        store.saveValidatedConnection(account, key("synthetic-old"))
        store.saveResetTimes(account, listOf("week" to Instant.ofEpochSecond(10)))
        val replacement = account.reconnect()
        assertTrue(store.saveValidatedConnection(replacement, key("synthetic-new"), account))
        assertEquals(account.id, replacement.id)
        assertNotEquals(account.generation, replacement.generation)
        assertNull(store.loadCredential(account))
        assertTrue(store.loadResetTimes(replacement).isEmpty())
        assertFalse(store.replaceCredential(account, key("synthetic-old"), key("synthetic-late")))
        assertFalse(store.saveResetTimes(account, listOf("week" to Instant.ofEpochSecond(30))))
        assertFalse(store.saveValidatedConnection(account.reconnect(), key("synthetic-late"), account))
        var published = 0
        assertNull(store.publishIfCurrent(account) { ++published })
        assertEquals(1, store.publishIfCurrent(replacement) { ++published })
        assertTrue(store.deleteConnection(replacement))
        assertNull(store.publishIfCurrent(replacement) { ++published })
        assertFalse(store.replaceCredential(replacement, key("synthetic-new"), key("synthetic-late")))
        assertEquals(1, published)
        assertTrue(store.loadConnections().isEmpty())
    }

    @Test fun `token rotation uses compare-and-set and clears absent optional fields`() {
        val store = EncryptedPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.CLAUDE)
        val old = Credential.ClaudeCredential("synthetic-old", "synthetic-r1", Instant.ofEpochSecond(50), "old", "old")
        val rotated = Credential.ClaudeCredential("synthetic-new", "synthetic-r2")
        store.saveValidatedConnection(account, old)
        assertTrue(store.replaceCredential(account, old, rotated))
        assertFalse(store.replaceCredential(account, old, old))
        assertEquals(rotated, store.loadCredential(account))
        assertEquals(account, store.loadConnections().single())
    }

    @Test fun `failed disk write does not advertise success or silently recover as empty`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        store.saveValidatedConnection(account, key("synthetic-before"))
        memory.failWrites = true
        assertThrows(IOException::class.java) { store.renameConnection(account, "Not durable") }
        assertEquals(listOf(account), store.connections.value)
        assertThrows(IOException::class.java) { store.loadConnections() }
        assertThrows(IOException::class.java) { store.loadCredential(account) }
        assertThrows(IOException::class.java) { store.deleteConnection(account) }
    }

    @Test fun `concurrent rotations have one winner and do not affect sibling`() {
        val store = EncryptedPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        val sibling = AccountConnection.create(AiService.OPENCODE_GO)
        store.saveValidatedConnection(account, key("synthetic-before"))
        store.saveValidatedConnection(sibling, key("synthetic-sibling"))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val results = (1..2).map { index -> pool.submit<Boolean> {
                check(start.await(5, TimeUnit.SECONDS))
                store.replaceCredential(account, key("synthetic-before"), key("synthetic-after-$index"))
            } }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            assertEquals("synthetic-sibling", store.loadCredential(sibling)?.accessToken)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun `connection keys and display names reject unsafe input`() {
        val valid = AccountConnection.create(AiService.CODEX)
        assertThrows(IllegalArgumentException::class.java) { valid.copy(id = "../other") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(id = "CLAUDE") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(generation = "") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(name = "\nspoofed") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(name = "x".repeat(81)) }
    }

    private fun key(value: String) = Credential.OpenCodeGoCredential(value)
}

/** Exercises Android preference semantics, not encryption; Keystore needs a device. */
internal class MemoryPreferences {
    val values = mutableMapOf<String, Any?>()
    var failWrites = false
    val prefs: SharedPreferences = mock(SharedPreferences::class.java) { call ->
        when (call.method.name) {
            "getAll" -> values.toMap()
            "contains" -> values.containsKey(call.getArgument<String>(0))
            "getString", "getLong", "getBoolean", "getFloat", "getInt", "getStringSet" ->
                values[call.getArgument<String>(0)] ?: call.getArgument<Any?>(1)
            "edit" -> editor()
            else -> error("Unexpected preference operation: ${call.method.name}")
        }
    }

    private fun editor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        var clear = false
        lateinit var editor: SharedPreferences.Editor
        editor = mock(SharedPreferences.Editor::class.java) { call ->
            when (call.method.name) {
                "putString", "putLong", "putBoolean", "putFloat", "putInt", "putStringSet" -> {
                    pending[call.getArgument<String>(0)] = call.getArgument<Any?>(1)
                    editor
                }
                "remove" -> { pending[call.getArgument<String>(0)] = null; editor }
                "clear" -> { clear = true; editor }
                "commit", "apply" -> {
                    if (clear) values.clear()
                    pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                    if (call.method.name == "commit") !failWrites else null
                }
                else -> error("Unexpected editor operation: ${call.method.name}")
            }
        }
        return editor
    }
}
