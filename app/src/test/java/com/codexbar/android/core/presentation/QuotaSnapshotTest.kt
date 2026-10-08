package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QuotaSnapshotTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val account = AccountConnection.create(AiService.CODEX)
    private val quota = QuotaInfo(AiService.CODEX, listOf(
        UsageWindow("Same label", 0.25, now.plusSeconds(100), "primary", durationSeconds = 300),
        UsageWindow("Same label", 1.2, null, "model", supplemental = true)
    ), null, fetchedAt = now, source = "native-oauth")

    @Test fun `transient failure and restart preserve measurement identities then auth invalidates`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val good = QuotaSnapshot().after(Result.Success(quota), now)
        val stale = good.after(Result.Failure(AppError.RateLimited), now.plusSeconds(30))
        cache.saveSnapshot(account, stale)
        val restored = WidgetPrefsManager(memory.prefs).getSnapshot(account.copy(name = "Renamed"), now.plusSeconds(60))
        assertEquals(quota, restored.quota)
        assertEquals(now.plusSeconds(30), restored.attemptedAt)
        assertEquals(Freshness.STALE, restored.freshness(now.plusSeconds(60)))
        assertEquals(75, QuotaPresentation.remainingPercent(QuotaPresentation.principal(quota).single().utilization))
        assertTrue(QuotaPresentation.summary(restored, now.plusSeconds(60)).contains("Stale"))
        assertNull(cache.getSnapshot(account.reconnect(), now).quota)
        val rejected = restored.after(Result.Failure(AppError.AuthError(AiService.CODEX, true)), now.plusSeconds(90))
        cache.saveSnapshot(account, rejected)
        assertNull(WidgetPrefsManager(memory.prefs).getSnapshot(account, now.plusSeconds(100)).quota)
        assertEquals(Freshness.RECONNECT, rejected.freshness(now))
    }

    @Test fun `expired or future readings are unavailable and expired cache is pruned`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        cache.saveSnapshot(account, QuotaSnapshot(quota))
        assertNull(cache.getSnapshot(account, now.plusSeconds(QuotaSnapshot.RETENTION_SECONDS + 1)).quota)
        assertNull(cache.getSnapshot(account, now).quota)
        assertEquals(Freshness.STALE, QuotaSnapshot(quota).freshness(now.minusSeconds(60)))
        assertEquals(Freshness.UNAVAILABLE, QuotaSnapshot(quota).freshness(now.minusSeconds(301)))
        assertEquals("Reset due · refresh to confirm", QuotaPresentation.reset(now, now.plusSeconds(1)))
        assertEquals(0, QuotaPresentation.remainingPercent(1.2))
    }
}
