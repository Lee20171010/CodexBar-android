package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QuotaPaceTest {
    private val now = 1_800_000_000L
    private fun quota(used: Double, at: Long, reset: Long? = now + 14400, source: String = "native-api",
        supplemental: Boolean = false, id: String = "primary") = QuotaInfo(AiService.CODEX, listOf(
        UsageWindow("5-Hour", used, reset?.let(Instant::ofEpochSecond), id, durationSeconds = 18000, supplemental = supplemental)
    ), null, fetchedAt = Instant.ofEpochSecond(at), source = source)

    @Test fun `gaps cycles accounts and missing reset never invent a continuous forecast`() {
        var history = QuotaPace.record(emptyMap(), quota(0.1, now - 3600), now - 3600)
        history = QuotaPace.record(history, quota(0.4, now - 1800), now - 1800)
        assertNull(QuotaPace.estimate(history["primary"], now))
        history = QuotaPace.record(history, quota(0.7, now), now)
        val estimate = QuotaPace.estimate(history["primary"], now)
        assertEquals(3, estimate?.samples?.size)
        assertNotNull(estimate?.runsOutAt)
        val reset = QuotaPace.record(history, quota(0.1, now + 60, now + 20000), now + 60)
        assertEquals(1, reset["primary"]?.samples?.size)
        val gap = QuotaPace.record(history, quota(0.8, now + 6 * 3600 + 1), now + 6 * 3600 + 1)
        assertEquals(1, gap["primary"]?.samples?.size)
        assertNull(QuotaPace.estimate(QuotaPace.record(emptyMap(), quota(0.5, now, reset = null), now)["primary"], now))
        assertTrue(QuotaPace.record(history, quota(0.9, now, source = "legacy"), now)["primary"]!!.samples.size == 3)
        assertTrue(QuotaPace.record(history, quota(0.2, now, supplemental = true, id = "model"), now).containsKey("primary"))
        assertFalse(QuotaPace.record(history, quota(0.2, now, supplemental = true, id = "model"), now).containsKey("model"))
    }

    @Test fun `history is generation scoped bounded and removed with the account`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        val sibling = AccountConnection.create(AiService.CODEX)
        repeat(60) {
            val at = now - 3600 + it * 60
            cache.recordHistory(account, quota(it / 100.0, at), Instant.ofEpochSecond(at))
        }
        assertEquals(48, cache.history(account, Instant.ofEpochSecond(now)).getValue("primary").samples.size)
        cache.recordHistory(sibling, quota(0.2, now), Instant.ofEpochSecond(now))
        val replacement = account.reconnect()
        assertTrue(WidgetPrefsManager(memory.prefs).history(replacement, Instant.ofEpochSecond(now)).isEmpty())
        cache.deleteAccountCache(account.id)
        assertTrue(cache.history(account).isEmpty())
        assertEquals(1, cache.history(sibling, Instant.ofEpochSecond(now)).getValue("primary").samples.size)
        assertFalse(memory.prefs.contains("history_${account.id}_generation"))
        assertTrue(cache.history(sibling, Instant.ofEpochSecond(now + 15 * 86400)).isEmpty())
    }

    @Test fun `duplicate stale future and backwards measurements cannot reset history`() {
        val initial = QuotaPace.record(emptyMap(), quota(0.1, now), now)
        assertEquals(initial, QuotaPace.record(initial, quota(0.5, now), now))
        assertEquals(initial, QuotaPace.record(initial, quota(0.5, now - 10), now))
        assertEquals(initial, QuotaPace.record(initial, quota(0.5, now + 1), now))
        assertTrue(QuotaPace.record(emptyMap(), quota(0.5, now - 301), now).isEmpty())
    }

    @Test fun `moving resets sources recovery and gaps break lines`() {
        var history = QuotaPace.record(emptyMap(), quota(0.4, now - 1800), now - 1800)
        history = QuotaPace.record(history, quota(0.5, now - 900), now - 900)
        for (reading in listOf(quota(0.6, now, reset = now + 15000),
            quota(0.6, now, source = "kotlin-api"), quota(0.1, now))) {
            assertEquals(1, QuotaPace.record(history, reading, now).getValue("primary").samples.size)
        }
        val gap = WindowHistory(now + 14400, 18000, listOf(PaceSample(now - 23000, 0.8),
            PaceSample(now - 900, 0.5), PaceSample(now, 0.4)))
        assertNull(QuotaPace.estimate(gap, now))
    }

    @Test fun `unknown duration supplies no guide and exhausted readings no future capacity`() {
        val samples = listOf(PaceSample(now - 1800, 0.8), PaceSample(now - 900, 0.6), PaceSample(now, 0.4))
        assertNull(QuotaPace.estimate(WindowHistory(null, null, samples), now)?.cycleEnd)
        assertNull(QuotaPace.estimate(WindowHistory(now, 18000, samples), now))
        assertNull(QuotaPace.estimate(WindowHistory(now + 14400, 18000,
            samples.dropLast(1) + PaceSample(now, -0.1)), now)?.runsOutAt)
        assertNull(QuotaPace.estimate(WindowHistory(now + 14400, 18000, samples), now - 1))
    }
}
