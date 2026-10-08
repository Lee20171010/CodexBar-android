package com.codexbar.android.core.widget

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.MemoryPreferences
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WidgetAccountCacheTest {
    @Test fun `legacy pins remain legacy IDs across restart and cache deletion`() {
        val memory = MemoryPreferences()
        memory.values["widget_7_services"] = setOf("CODEX")
        val cache = WidgetPrefsManager(memory.prefs)
        val sibling = AccountConnection.create(AiService.CODEX)
        cache.cacheQuota(sibling, quota())
        cache.deleteAccountCache("CODEX")
        assertEquals(setOf("CODEX"), WidgetPrefsManager(memory.prefs).getSelectedConnections(7))
        assertNotNull(cache.getCachedQuota(sibling))
    }

    @Test fun `generation snapshot preserves actual measurement age and sibling data`() {
        val cache = WidgetPrefsManager(MemoryPreferences().prefs)
        val first = AccountConnection.create(AiService.CODEX)
        val sibling = AccountConnection.create(AiService.CODEX)
        cache.cacheQuota(first, quota()); cache.cacheQuota(sibling, quota())
        val renamed = first.copy(name = "Renamed")
        assertEquals(quota().fetchedAt, cache.getCachedQuota(renamed)?.fetchedAt)
        assertEquals("Window, with comma", cache.getCachedQuota(first)?.windows?.single()?.label)
        val replacement = first.reconnect()
        assertNull(cache.getCachedQuota(replacement))
        cache.cacheQuota(replacement, quota())
        assertNull(cache.getCachedQuota(first))
        cache.deleteAccountCache(first.id)
        assertNotNull(cache.getCachedQuota(sibling))
    }

    @Test fun `absent or invalid utilization is unknown instead of zero`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.cacheQuota(account, quota())
        val key = "cache_${account.id}_Window, with comma_util"
        memory.values.remove(key)
        assertNull(cache.getCachedQuota(account))
        memory.values[key] = Float.NaN
        assertNull(cache.getCachedQuota(account))
    }

    private fun quota() = QuotaInfo(AiService.CODEX, listOf(UsageWindow("Window, with comma", 0.5, null)),
        null, "Pro", Instant.ofEpochSecond(1000))
}
