package com.codexbar.android.feature.settings

import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Battery-exemption offer policy: never on startup or default restore, once per
 * manual/disabled -> enabled change, persisted so "Not now" is not repeated.
 */
class BatteryOfferTest {

    @Test
    fun `startup with an enabled schedule never offers`() {
        assertFalse(SettingsViewModel.shouldOfferBattery(minutes = 30, previousMinutes = 30, offerShown = false, exempt = false))
    }

    @Test
    fun `manual or disabled to enabled offers once`() {
        assertTrue(SettingsViewModel.shouldOfferBattery(minutes = 30, previousMinutes = 0, offerShown = false, exempt = false))
    }

    @Test
    fun `declined offer is not repeated after restart`() {
        assertFalse(SettingsViewModel.shouldOfferBattery(minutes = 60, previousMinutes = 0, offerShown = true, exempt = false))
    }

    @Test
    fun `device already exempt never offers`() {
        assertFalse(SettingsViewModel.shouldOfferBattery(minutes = 30, previousMinutes = 0, offerShown = false, exempt = true))
    }

    @Test
    fun `switching back to manual does not offer`() {
        assertFalse(SettingsViewModel.shouldOfferBattery(minutes = 0, previousMinutes = 30, offerShown = false, exempt = false))
    }

    @Test
    fun `offer flag persists across restart`() {
        val memory = MemoryPreferences()
        val store = EncryptedPrefsManager(memory.prefs)
        assertFalse(store.isBatteryOfferShown())
        store.setBatteryOfferShown(true)
        assertTrue(EncryptedPrefsManager(memory.prefs).isBatteryOfferShown())
    }
}
