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

    @Test fun `global defaults apply until a connection overrides them`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        val global = DisplayOptions(fillUsed = true, showMarkers = false)
        cache.saveGlobalDisplayOptions(global)
        assertEquals(global, WidgetPrefsManager(memory.prefs).globalDisplayOptions())
        assertEquals(global, cache.effectiveDisplayOptions(account))
        val own = DisplayOptions(absoluteReset = true)
        cache.saveDisplayOptions(account, own)
        assertEquals(own, cache.effectiveDisplayOptions(account))
        assertEquals(global, cache.effectiveDisplayOptions(AccountConnection.create(AiService.CODEX)))
        cache.deleteAccountCache(account.id)
        assertEquals(global, cache.effectiveDisplayOptions(account))
    }

    @Test fun `legacy stored options decode with new display defaults`() {
        // Options saved before fillUsed/showMarkers existed keep remaining-fill and markers.
        val legacy = """{"hiddenWindows":["secondary"],"showAmounts":false,"absoluteReset":true}"""
        val decoded = kotlinx.serialization.json.Json.decodeFromString<DisplayOptions>(legacy)
        assertEquals(DisplayOptions(setOf("secondary"), showAmounts = false, absoluteReset = true), decoded)
    }

    @Test fun `per-connection accent and headline window round-trip`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        val options = DisplayOptions(accent = "green", headlineWindow = "weekly")
        cache.saveDisplayOptions(account, options)
        assertEquals(options, WidgetPrefsManager(memory.prefs).effectiveDisplayOptions(account))
    }

    @Test fun `warning markers mirror with the fill direction`() {
        assertEquals(listOf(.5f, .2f), DisplayOptions().markerFractions())
        assertEquals(listOf(.5f, .8f), DisplayOptions(fillUsed = true).markerFractions())
        assertTrue(DisplayOptions(showMarkers = false).markerFractions().isEmpty())
    }

    @Test fun `global edits propagate through accent-only overrides`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveDisplayOptions(account, DisplayOptions(accent = "green"), touched = setOf("accent"))
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true, workDays = 5))
        val effective = cache.effectiveDisplayOptions(account)
        assertEquals("green", effective.accent)
        assertEquals(true, effective.fillUsed) // the accent edit must not freeze the global fill
        assertEquals(5, effective.workDays)
    }

    @Test fun `explicit account and widget choices survive later global edits`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = false))
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true), touched = setOf("fillUsed"))
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true, showAmounts = false), 7, setOf("showAmounts"))
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = false, workDays = 5))
        val effective = cache.effectiveDisplayOptions(account)
        assertEquals(true, effective.fillUsed) // explicit account choice kept
        assertEquals(5, effective.workDays) // untouched field follows the new global
        val widget = cache.effectiveDisplayOptions(account, 7)
        assertEquals(false, widget.showAmounts) // explicit at widget level
        assertEquals(true, widget.fillUsed) // inherits the account choice
        assertEquals(5, widget.workDays) // follows global through the chain
    }

    @Test fun `explicit choice equal to parent survives an unrelated edit`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        // fillUsed=true converges with the global value, then an accent edit must not demote it
        // back to inheritance, or a later global false would leak through.
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true))
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true), touched = setOf("fillUsed"))
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true, accent = "green"), touched = setOf("accent"))
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = false))
        val effective = cache.effectiveDisplayOptions(account)
        assertEquals(true, effective.fillUsed)
        assertEquals("green", effective.accent)
    }

    @Test fun `choosing auto pins a null headline over the parent`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveGlobalDisplayOptions(DisplayOptions(headlineWindow = "weekly"))
        cache.saveDisplayOptions(account, DisplayOptions(headlineWindow = null), touched = setOf("headlineWindow"))
        cache.saveGlobalDisplayOptions(DisplayOptions(headlineWindow = "daily"))
        assertNull(cache.effectiveDisplayOptions(account).headlineWindow)
    }

    @Test fun `reset restores the inheritance chain`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true))
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = false, accent = "red"), touched = setOf("fillUsed", "accent"))
        assertEquals("red", cache.effectiveDisplayOptions(account).accent)
        cache.clearDisplayOptions(account)
        assertEquals(DisplayOptions(fillUsed = true), cache.effectiveDisplayOptions(account))
        cache.saveDisplayOptions(account, DisplayOptions(showMarkers = false), 7, setOf("showMarkers"))
        cache.clearDisplayOptions(account, 7)
        assertEquals(cache.effectiveDisplayOptions(account), cache.effectiveDisplayOptions(account, 7))
    }

    @Test fun `legacy full-profile entries stay frozen until reset`() {
        val memory = MemoryPreferences()
        val account = AccountConnection.create(AiService.CODEX)
        memory.prefs.edit().putString("display_${account.id}", """{"fillUsed":true}""").commit()
        val cache = WidgetPrefsManager(memory.prefs)
        cache.saveGlobalDisplayOptions(DisplayOptions(showAmounts = false))
        val legacy = cache.effectiveDisplayOptions(account)
        assertEquals(true, legacy.fillUsed)
        assertEquals(true, legacy.showAmounts) // a legacy profile means defaults, not inheritance
        cache.saveDisplayOptions(account, legacy.copy(accent = "green"), touched = setOf("accent"))
        // Resaving a legacy profile pins every one of its fields, not just the edited one.
        cache.saveGlobalDisplayOptions(DisplayOptions(showAmounts = false, absoluteReset = true))
        val resaved = cache.effectiveDisplayOptions(account)
        assertEquals(true, resaved.showAmounts)
        assertEquals(false, resaved.absoluteReset) // frozen until an explicit reset
        assertEquals("green", resaved.accent)
        cache.clearDisplayOptions(account)
        assertEquals(true, cache.effectiveDisplayOptions(account).absoluteReset) // reset now inherits
    }

    @Test fun `display edits emit a shared revision`() {
        val cache = WidgetPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.CODEX)
        val start = cache.displayRevision.value
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true))
        cache.saveDisplayOptions(account, DisplayOptions(accent = "green"))
        cache.clearDisplayOptions(account)
        assertEquals(start + 3, cache.displayRevision.value)
    }

    @Test fun `stale edit DTO cannot overwrite a pinned field`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true))
        cache.saveDisplayOptions(account, DisplayOptions(accent = "red"), touched = setOf("accent"))
        // A stale DTO captured before the accent edit, now toggling only showAmounts.
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true, showAmounts = false), touched = setOf("showAmounts"))
        val effective = cache.effectiveDisplayOptions(account)
        assertEquals("red", effective.accent) // the stale null must not clear the pinned accent
        assertEquals(false, effective.showAmounts)
        assertEquals(true, effective.fillUsed)
    }

    @Test fun `widget reset then edit starts from the parent`() {
        val memory = MemoryPreferences()
        val cache = WidgetPrefsManager(memory.prefs)
        val account = AccountConnection.create(AiService.CODEX)
        cache.saveDisplayOptions(account, DisplayOptions(showAmounts = false), 7, setOf("showAmounts"))
        cache.clearDisplayOptions(account, 7) // Confirm applies the deferred reset first
        cache.saveDisplayOptions(account, DisplayOptions(fillUsed = true), 7, setOf("fillUsed"))
        val widget = cache.effectiveDisplayOptions(account, 7)
        assertEquals(true, widget.showAmounts) // the pre-reset pin is gone; the draft came from the parent
        assertEquals(true, widget.fillUsed)
    }

    @Test fun `unknown touched field is rejected`() {
        val cache = WidgetPrefsManager(MemoryPreferences().prefs)
        val account = AccountConnection.create(AiService.CODEX)
        assertThrows(IllegalArgumentException::class.java) {
            cache.saveDisplayOptions(account, DisplayOptions(), touched = setOf("headline"))
        }
    }
}
