package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DisplayOptionsTest {
    @Test fun `hidden stable metrics remain risky and return hidden after disappearing`() {
        val window = UsageWindow("Weekly", 1.0, null, "secondary")
        val quota = QuotaInfo(AiService.CODEX, listOf(window), null, fetchedAt = Instant.now())
        val display = DisplayOptions(setOf("secondary"))
        assertTrue(display.visible(quota).isEmpty())
        assertTrue(display.hiddenRisk(quota))
        assertFalse(display.hiddenRisk(quota.copy(windows = emptyList())))
        assertTrue(display.visible(quota.copy(windows = listOf(window.copy(label = "每週")))) .isEmpty())
        assertEquals(listOf(window), DisplayOptions().visible(quota))
    }

    @Test fun `account and widget choices persist independently and delete with their owner`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        val sibling = AccountConnection.create(AiService.CODEX)
        val options = DisplayOptions(setOf("primary"), showAmounts = false, absoluteReset = true)
        cache.saveDisplayOptions(account, options)
        assertEquals(options, WidgetPrefsManager(memory.prefs).displayOptions(account))
        assertEquals(DisplayOptions(), cache.displayOptions(account, 1))
        cache.saveDisplayOptions(account, options, 1)
        assertEquals(DisplayOptions(), cache.displayOptions(account, 2))
        assertEquals(DisplayOptions(), cache.displayOptions(sibling))
        cache.deleteWidgetConfig(1)
        assertEquals(DisplayOptions(), cache.displayOptions(account, 1))
        assertEquals(options, cache.displayOptions(account.reconnect()))
        cache.saveDisplayOptions(account, options, 2)
        cache.deleteAccountCache(account.id)
        assertEquals(DisplayOptions(), cache.displayOptions(account))
        assertEquals(DisplayOptions(), cache.displayOptions(account, 2))
    }
}
