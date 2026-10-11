package com.codexbar.android.core.notification

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QuotaRecoveryTest {
    private val now = Instant.now().epochSecond
    private fun win(
        used: Double, reset: Long? = now + 18000, duration: Long? = 18000,
        slot: UsageWindowSlot = UsageWindowSlot.PRIMARY, id: String = slot.name
    ) = UsageWindow(id, used, reset?.let(Instant::ofEpochSecond), id = id, durationSeconds = duration, slot = slot)

    private fun quota(
        service: AiService, vararg windows: UsageWindow, at: Long = now,
        tier: String? = null, source: String = "native-api"
    ) = QuotaInfo(service, windows.toList(), null, tier, fetchedAt = Instant.ofEpochSecond(at), source = source)

    private fun step(prev: Map<String, RecoveryReading>, q: QuotaInfo, at: Long, notifyEnabled: Boolean = true) =
        QuotaRecovery.observe(prev, q, at, notifyEnabled)

    @Test fun `session reset requires boundary advance and honors the 120s rule`() {
        val b = now + 18000
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, b), at = now), now)
        assertTrue(s0.first["session:PRIMARY"]!!.wasAbove)
        // Backward boundary: no post, baseline and known boundary preserved.
        val s1 = step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, b - 600), at = now + 60), now + 60)
        assertTrue(s1.second.isEmpty())
        assertTrue(s1.first["session:PRIMARY"]!!.wasAbove)
        assertEquals(b, s1.first["session:PRIMARY"]!!.boundary)
        // 119s drift is not an advance; exactly 120s is.
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO, win(0.005, b + 119), at = now + 120), now + 120)
        assertTrue(s2.second.isEmpty())
        val s3 = step(s2.first, quota(AiService.OPENCODE_GO, win(0.005, b + 120), at = now + 180), now + 180)
        assertEquals(1, s3.second.size)
        val posted = s3.first["session:PRIMARY"]!!
        assertFalse(posted.wasAbove)
        assertTrue(posted.receiptPresent)
        assertEquals(b + 120, posted.lastNotifiedBoundary)
        // Duplicate timestamp cannot re-fire; same boundary cannot re-fire; new cycle rearms.
        assertTrue(step(s3.first, quota(AiService.OPENCODE_GO, win(0.005, b + 120), at = now + 180), now + 180).second.isEmpty())
        val s5 = step(s3.first, quota(AiService.OPENCODE_GO, win(0.5, b + 120), at = now + 240), now + 240).first
        assertFalse(s5["session:PRIMARY"]!!.receiptPresent)
        assertTrue(step(s5, quota(AiService.OPENCODE_GO, win(0.005, b + 120), at = now + 300), now + 300).second.isEmpty())
        assertEquals(1, step(s5, quota(AiService.OPENCODE_GO, win(0.005, b + 500), at = now + 360), now + 360).second.size)
    }

    @Test fun `nil boundary posts once and late metadata resolves without second notice`() {
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, null), at = now), now)
        val s1 = step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, null), at = now + 60), now + 60)
        assertEquals(1, s1.second.size)
        assertTrue(s1.first["session:PRIMARY"]!!.receiptPresent)
        assertNull(s1.first["session:PRIMARY"]!!.receiptBoundary)
        // Reset metadata returns: receipt resolves to the boundary, no second notice.
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO, win(0.005, now + 18000), at = now + 120), now + 120)
        assertTrue(s2.second.isEmpty())
        assertEquals(now + 18000, s2.first["session:PRIMARY"]!!.receiptBoundary)
        assertEquals(now + 18000, s2.first["session:PRIMARY"]!!.lastNotifiedBoundary)
        // New usage cycle clears the receipt; the next advanced boundary posts.
        val s3 = step(s2.first, quota(AiService.OPENCODE_GO, win(0.5, now + 18000), at = now + 180), now + 180).first
        assertFalse(s3["session:PRIMARY"]!!.receiptPresent)
        assertEquals(1, step(s3, quota(AiService.OPENCODE_GO, win(0.005, now + 36000), at = now + 240), now + 240).second.size)
    }

    @Test fun `codex weekly advanced boundary requires previous boundary`() {
        val b = now + 604_800
        val cw = { used: Double, reset: Long?, at: Long ->
            quota(AiService.CODEX, win(used, reset, 604_800, UsageWindowSlot.SECONDARY), tier = "plus", at = at)
        }
        val s0 = step(emptyMap(), cw(0.5, null, now), now)
        assertEquals("plus", s0.first["weekly:SECONDARY"]!!.plan)
        // First crossing without a previous boundary: Codex weekly refuses (requiresPrevious).
        val s1 = step(s0.first, cw(0.03, b, now + 60), now + 60)
        assertTrue(s1.second.isEmpty())
        val s2 = step(s1.first, cw(0.5, b, now + 120), now + 120)
        assertEquals(1, step(s2.first, cw(0.03, b + 604_800, now + 180), now + 180).second.size)
    }

    @Test fun `codex same-boundary candidate confirms between 60 and 1800 seconds`() {
        val b = now + 3600
        val cw = { used: Double, at: Long, tier: String ->
            quota(AiService.CODEX, win(used, b, 604_800, UsageWindowSlot.SECONDARY), tier = tier, at = at)
        }
        val s0 = step(emptyMap(), cw(0.5, now, "plus"), now)
        assertTrue(s0.first["weekly:SECONDARY"]!!.wasAboveCandidate)
        // >25% to <=5% on the same future boundary starts a candidate; no notice yet.
        val s1 = step(s0.first, cw(0.03, now + 1, "plus"), now + 1)
        assertTrue(s1.first["weekly:SECONDARY"]!!.pendingCandidate)
        assertTrue(step(s1.first, cw(0.03, now + 31, "plus"), now + 31).second.isEmpty()) // age 30 too early
        val s3 = step(s1.first, cw(0.03, now + 61, "plus"), now + 61)
        assertEquals(1, s3.second.size) // age 60 confirms
        assertTrue(step(s3.first, cw(0.03, now + 121, "plus"), now + 121).second.isEmpty()) // already posted
        // Expired candidate re-evaluates the baseline against 25% and never posts.
        val e1 = step(s1.first, cw(0.03, now + 1802, "plus"), now + 1802)
        assertTrue(e1.second.isEmpty())
        assertFalse(e1.first["weekly:SECONDARY"]!!.pendingCandidate) // age guard expires compatibility, not just confirmation
        assertFalse(e1.first["weekly:SECONDARY"]!!.wasAboveCandidate)
        // A plan change blocks confirmation and clears the posted boundary.
        val p1 = step(s1.first, cw(0.03, now + 61, "pro"), now + 61)
        assertTrue(p1.second.isEmpty())
        assertFalse(p1.first["weekly:SECONDARY"]!!.pendingCandidate)
        assertNull(p1.first["weekly:SECONDARY"]!!.postedBoundary)
    }

    @Test fun `claude weekly rearm needs two samples above one percent`() {
        val b = now + 604_800
        val cw = { used: Double, reset: Long, at: Long ->
            quota(AiService.CLAUDE, win(used, reset, 604_800, UsageWindowSlot.SECONDARY), at = at)
        }
        val s0 = step(emptyMap(), cw(0.5, b, now), now)
        val s1 = step(s0.first, cw(0.005, b, now + 60), now + 60)
        assertEquals(1, s1.second.size)
        assertEquals(0, s1.first["weekly:SECONDARY"]!!.recoveryCount)
        val s2 = step(s1.first, cw(0.5, b, now + 120), now + 120)
        assertEquals(1, s2.first["weekly:SECONDARY"]!!.recoveryCount)
        assertFalse(s2.first["weekly:SECONDARY"]!!.wasAbove)
        val s3 = step(s2.first, cw(0.5, b, now + 180), now + 180)
        assertNull(s3.first["weekly:SECONDARY"]!!.recoveryCount)
        assertTrue(s3.first["weekly:SECONDARY"]!!.wasAbove)
        assertEquals(1, step(s3.first, cw(0.005, b + 604_800, now + 240), now + 240).second.size)
    }

    @Test fun `weekly lane eligibility is proven per provider`() {
        // Generic providers need exactly one 10080-minute window; two matches are ambiguous.
        val seed = step(emptyMap(), quota(AiService.GEMINI,
            win(0.5, duration = 604_800, slot = UsageWindowSlot.SECONDARY),
            win(0.5, duration = 604_800, slot = UsageWindowSlot.TERTIARY)), now)
        assertTrue(step(seed.first, quota(AiService.GEMINI,
            win(0.005, duration = 604_800, slot = UsageWindowSlot.SECONDARY),
            win(0.005, duration = 604_800, slot = UsageWindowSlot.TERTIARY), at = now + 60), now + 60).second.isEmpty())
        // Missing or off-by-one duration has no weekly lane.
        assertTrue(step(emptyMap(), quota(AiService.GEMINI,
            win(0.5, duration = 604_801, slot = UsageWindowSlot.SECONDARY)), now).first.isEmpty())
        assertTrue(step(emptyMap(), quota(AiService.CODEX,
            win(0.5, duration = null, slot = UsageWindowSlot.SECONDARY)), now).first.isEmpty())
        // TERTIARY is never a reset lane.
        assertTrue(step(emptyMap(), quota(AiService.CODEX,
            win(0.5, duration = 604_800, slot = UsageWindowSlot.TERTIARY)), now).first.isEmpty())
        // Generic weekly has no boundary gate: crossing alone posts.
        val one = step(emptyMap(), quota(AiService.GEMINI,
            win(0.5, duration = 604_800, slot = UsageWindowSlot.SECONDARY)), now)
        assertEquals(1, step(one.first, quota(AiService.GEMINI,
            win(0.005, duration = 604_800, slot = UsageWindowSlot.SECONDARY), at = now + 60), now + 60).second.size)
    }

    @Test fun `source change suppresses session crossing but not weekly`() {
        val b = now + 18000
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, b)), now)
        assertTrue(step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, b + 3600), at = now + 60, source = "other"), now + 60).second.isEmpty())
        val w0 = step(emptyMap(), quota(AiService.CLAUDE,
            win(0.5, b, 604_800, UsageWindowSlot.SECONDARY)), now)
        assertEquals(1, step(w0.first, quota(AiService.CLAUDE,
            win(0.005, b, 604_800, UsageWindowSlot.SECONDARY), at = now + 60, source = "other"), now + 60).second.size)
    }

    @Test fun `stale cache and legacy source cannot feed the detector`() {
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5)), now)
        assertTrue(step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, now + 3600), at = now - 301), now).second.isEmpty())
        assertTrue(step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, now + 3600), source = "legacy"), now).second.isEmpty())
    }

    @Test fun `legacy record migrates conservatively with suppression floor`() {
        val legacy = RecoveryReading(at = now - 60, used = 0.5, source = "native-api", notifiedUntil = now + 600)
        val s1 = step(mapOf("PRIMARY" to legacy),
            quota(AiService.OPENCODE_GO, win(0.005, now + 5400)), now)
        assertTrue(s1.second.isEmpty()) // old cooldown suppresses the migrated crossing
        val migrated = s1.first["session:PRIMARY"]!!
        assertEquals(1, migrated.v)
        assertFalse(migrated.wasAbove) // the legitimate transition is consumed, only the notice is gated
        assertTrue(migrated.receiptPresent) // suppressed cycle still reserves its receipt
        assertEquals(now + 600, migrated.migrationFloorUntil)
        // Floor expires on its own; a later cycle posts normally.
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO, win(0.5, now + 5400), at = now + 601), now + 601).first
        assertEquals(0L, s2["session:PRIMARY"]!!.migrationFloorUntil)
        assertFalse(s2["session:PRIMARY"]!!.receiptPresent)
        assertEquals(1, step(s2, quota(AiService.OPENCODE_GO, win(0.005, now + 9000), at = now + 602), now + 602).second.size)
        // Without a cooldown the migrated baseline posts on a fresh boundary advance.
        val open = RecoveryReading(at = now - 60, used = 0.5, source = "native-api")
        assertEquals(1, step(mapOf("PRIMARY" to open),
            quota(AiService.OPENCODE_GO, win(0.005, now + 5400)), now).second.size)
    }

    @Test fun `absent lane keeps its receipt so returning metadata cannot re-alert`() {
        val b = now + 18000
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, b)), now)
        val s1 = step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, b + 3600), at = now + 60), now + 60)
        assertEquals(1, s1.second.size)
        // A reading without the lane (missing metadata) must not drop lane state.
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO), now + 120)
        assertTrue(s2.first.containsKey("session:PRIMARY"))
        assertTrue(s2.first["session:PRIMARY"]!!.receiptPresent)
        // The same crossing returns: no re-alert.
        assertTrue(step(s2.first, quota(AiService.OPENCODE_GO, win(0.005, b + 3600), at = now + 180), now + 180).second.isEmpty())
    }

    @Test fun `disabled toggle runs the detector without reserving receipts`() {
        val b = now + 18000
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, b)), now, notifyEnabled = false)
        val s1 = step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, b + 3600), at = now + 60), now + 60, notifyEnabled = false)
        assertTrue(s1.second.isEmpty())
        val off = s1.first["session:PRIMARY"]!!
        assertFalse(off.receiptPresent) // no unposted receipt may be created while disabled
        assertFalse(off.wasAbove) // the detector itself keeps running, like Mac
        assertNull(off.lastNotifiedBoundary)
        // Enabling later still alerts on the next confirmed cycle.
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO, win(0.5, b + 3600), at = now + 120), now + 120).first
        assertEquals(1, step(s2, quota(AiService.OPENCODE_GO, win(0.005, b + 7200), at = now + 180), now + 180).second.size)
        // Same-boundary coverage guard (Mac :61-63): a confirmed weekly cycle inside an
        // already-notified boundary range reserves its receipt even while disabled.
        val g0 = step(emptyMap(), quota(AiService.GEMINI, win(0.5, b, 604_800, UsageWindowSlot.SECONDARY)), now)
        val g1 = step(g0.first, quota(AiService.GEMINI, win(0.005, b, 604_800, UsageWindowSlot.SECONDARY), at = now + 60), now + 60)
        assertEquals(1, g1.second.size) // generic weekly has no boundary gate
        val g2 = step(g1.first, quota(AiService.GEMINI, win(0.5, b - 60, 604_800, UsageWindowSlot.SECONDARY), at = now + 120), now + 120).first
        val g3 = step(g2, quota(AiService.GEMINI, win(0.005, b - 60, 604_800, UsageWindowSlot.SECONDARY), at = now + 180), now + 180, notifyEnabled = false)
        assertTrue(g3.second.isEmpty())
        assertTrue(g3.first["weekly:SECONDARY"]!!.receiptPresent) // reserved without notice
    }

    @Test fun `receipt rearm and late metadata run while disabled`() {
        // Counterexample: a nil-boundary receipt must rearm on rising usage while disabled,
        // so a later enabled cycle can still post.
        val s0 = step(emptyMap(), quota(AiService.OPENCODE_GO, win(0.5, null)), now)
        val s1 = step(s0.first, quota(AiService.OPENCODE_GO, win(0.005, null), at = now + 60), now + 60)
        assertEquals(1, s1.second.size)
        assertTrue(s1.first["session:PRIMARY"]!!.receiptPresent)
        val s2 = step(s1.first, quota(AiService.OPENCODE_GO, win(0.5, null), at = now + 120), now + 120, notifyEnabled = false)
        assertFalse(s2.first["session:PRIMARY"]!!.receiptPresent) // rearm ran while disabled
        val s3 = step(s2.first, quota(AiService.OPENCODE_GO, win(0.005, null), at = now + 180), now + 180, notifyEnabled = false)
        assertTrue(s3.second.isEmpty()) // emission stays gated while disabled
        assertFalse(s3.first["session:PRIMARY"]!!.receiptPresent) // fresh nil-boundary cycle not reserved while OFF
        val s4 = step(s3.first, quota(AiService.OPENCODE_GO, win(0.5, null), at = now + 240), now + 240).first
        assertEquals(1, step(s4, quota(AiService.OPENCODE_GO, win(0.005, null), at = now + 300), now + 300).second.size)
        // Late boundary metadata resolves the announced cycle's receipt while disabled, no duplicate notice.
        val r = step(s1.first, quota(AiService.OPENCODE_GO, win(0.005, now + 18000), at = now + 120), now + 120, notifyEnabled = false)
        assertTrue(r.second.isEmpty())
        assertEquals(now + 18000, r.first["session:PRIMARY"]!!.receiptBoundary)
        assertEquals(now + 18000, r.first["session:PRIMARY"]!!.lastNotifiedBoundary)
    }

    @Test fun `disabled storage path reserves no receipt and enabling rearms`() {
        val memory = MemoryPreferences()
        val prefs = EncryptedPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.OPENCODE_GO)
        prefs.saveValidatedConnection(account, Credential.OpenCodeGoCredential("synthetic"))
        assertFalse(prefs.isRecoveryAlertsEnabled())
        val b = now + 18000
        prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.5, b), at = now - 180))
        assertTrue(prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.005, b + 3600), at = now - 120)).isEmpty())
        val stored = kotlinx.serialization.json.Json.decodeFromString<Map<String, RecoveryReading>>(
            memory.prefs.getString("${account.id}_recovery_v1", "{}")!!)
        assertFalse(stored["session:PRIMARY"]!!.receiptPresent) // nothing reserved while disabled
        prefs.setRecoveryAlertsEnabled(true)
        assertTrue(prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.5, b + 3600), at = now - 60)).isEmpty())
        assertEquals(1, prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.005, b + 7200))).size)
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
        val b = now + 18000
        prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.5, b), at = now - 60))
        assertEquals(1, prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.005, b + 3600))).size)
        assertTrue(EncryptedPrefsManager(memory.prefs).recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.005, b + 3600))).isEmpty())
        assertTrue(prefs.recordRecovery(sibling, quota(AiService.OPENCODE_GO, win(0.005, b + 3600))).isEmpty())
        val replacement = account.reconnect()
        prefs.saveValidatedConnection(replacement, key, account)
        assertTrue(prefs.recordRecovery(account, quota(AiService.OPENCODE_GO, win(0.5, b))).isEmpty())
        assertTrue(prefs.recordRecovery(replacement, quota(AiService.OPENCODE_GO, win(0.005, b + 3600))).isEmpty())
        prefs.deleteConnection(replacement)
        assertTrue(prefs.recordRecovery(replacement, quota(AiService.OPENCODE_GO, win(0.5, b))).isEmpty())
        assertNotNull(prefs.loadCredential(sibling))
    }
}
