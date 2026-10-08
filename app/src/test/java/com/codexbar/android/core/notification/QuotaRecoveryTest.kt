package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QuotaRecoveryTest {
    private val now = Instant.now().epochSecond
    private fun quota(used: Double, at: Long = now, reset: Long? = now + 18000, source: String = "native-api") =
        QuotaInfo(AiService.OPENCODE_GO, listOf(UsageWindow("5-Hour", used, reset?.let(Instant::ofEpochSecond),
            id = "primary", durationSeconds = 18000)), null, fetchedAt = Instant.ofEpochSecond(at), source = source)

    @Test fun `timestamps jitter first cache and clock movement cannot prove recovery`() {
        assertTrue(QuotaRecovery.observe(emptyMap(), quota(0.1), now).second.isEmpty())
        val old = QuotaRecovery.observe(emptyMap(), quota(0.98, now - 60), now - 60).first
        for (current in listOf(quota(0.98, reset = now + 36000), quota(0.94), quota(0.2, now - 60),
            quota(0.2, now + 1), quota(0.2, now - 301), quota(0.2, source = "legacy"), quota(0.2, source = "other"))) {
            assertTrue(QuotaRecovery.observe(old, current, now).second.isEmpty())
        }
        assertEquals(1, QuotaRecovery.observe(old, quota(0.7, reset = null), now).second.size)
    }

    @Test fun `durable cycle cooldown suppresses repeated and rolling reset events`() {
        val old = QuotaRecovery.observe(emptyMap(), quota(1.0, now - 60), now - 60).first
        val recovered = QuotaRecovery.observe(old, quota(0.2), now)
        assertEquals(1, recovered.second.size)
        assertTrue(QuotaRecovery.observe(recovered.first, quota(0.2), now).second.isEmpty())
        val exhausted = QuotaRecovery.observe(recovered.first, quota(1.0, now + 60), now + 60).first
        assertTrue(QuotaRecovery.observe(exhausted, quota(0.2, now + 120, now + 40000), now + 120).second.isEmpty())
        val later = QuotaRecovery.observe(exhausted, quota(1.0, now + 18001), now + 18001).first
        assertEquals(1, QuotaRecovery.observe(later, quota(0.2, now + 18061), now + 18061).second.size)
    }

    @Test fun `receipt survives restart but not reconnect and keeps siblings isolated`() {
        val memory = MemoryPreferences()
        val prefs = EncryptedPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        val sibling = AccountConnection.create(AiService.OPENCODE_GO)
        val key = Credential.OpenCodeGoCredential("synthetic")
        prefs.saveValidatedConnection(account, key)
        prefs.saveValidatedConnection(sibling, key)
        assertFalse(prefs.isRecoveryAlertsEnabled())
        prefs.setRecoveryAlertsEnabled(true)
        prefs.recordRecovery(account, quota(1.0, now - 60))
        assertEquals(1, prefs.recordRecovery(account, quota(0.2)).size)
        assertTrue(EncryptedPrefsManager(memory.prefs).recordRecovery(account, quota(0.2)).isEmpty())
        assertTrue(prefs.recordRecovery(sibling, quota(0.2)).isEmpty())
        val replacement = account.reconnect()
        prefs.saveValidatedConnection(replacement, key, account)
        assertTrue(prefs.recordRecovery(account, quota(1.0)).isEmpty())
        assertTrue(prefs.recordRecovery(replacement, quota(0.2)).isEmpty())
        prefs.deleteConnection(replacement)
        assertTrue(prefs.recordRecovery(replacement, quota(1.0)).isEmpty())
        assertNotNull(prefs.loadCredential(sibling))
    }
}
