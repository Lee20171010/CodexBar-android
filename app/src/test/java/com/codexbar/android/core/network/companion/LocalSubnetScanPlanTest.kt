package com.codexbar.android.core.network.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSubnetScanPlanTest {

    @Test
    fun `stays inside the subnet the device is attached to`() {
        val candidates = LocalSubnetScanPlan.candidates(
            localAddress = "192.168.1.57",
            prefixLength = 24,
            previousHost = null
        )

        assertEquals(253, candidates.size)
        assertTrue(candidates.all { it.startsWith("192.168.1.") })
        assertFalse(candidates.contains("192.168.1.57"))
        assertFalse(candidates.contains("192.168.1.0"))
        assertFalse(candidates.contains("192.168.1.255"))
    }

    @Test
    fun `a wide prefix is narrowed so a reconnect never sweeps the network`() {
        val candidates = LocalSubnetScanPlan.candidates(
            localAddress = "10.4.7.20",
            prefixLength = 16,
            previousHost = null
        )

        assertTrue(candidates.size <= LocalSubnetScanPlan.MAX_CANDIDATES)
        assertTrue(candidates.all { it.startsWith("10.4.7.") })
    }

    @Test
    fun `addresses near the previous lease are probed first`() {
        val candidates = LocalSubnetScanPlan.candidates(
            localAddress = "192.168.1.57",
            prefixLength = 24,
            previousHost = "192.168.1.40"
        )

        assertEquals(listOf("192.168.1.39", "192.168.1.41", "192.168.1.38"), candidates.take(3))
        assertFalse(candidates.contains("192.168.1.40"))
    }

    @Test
    fun `a previous lease from another subnet does not reorder the plan`() {
        val candidates = LocalSubnetScanPlan.candidates(
            localAddress = "192.168.1.57",
            prefixLength = 24,
            previousHost = "10.0.0.5"
        )

        assertEquals(listOf("192.168.1.1", "192.168.1.2", "192.168.1.3"), candidates.take(3))
    }

    @Test
    fun `excluded and malformed inputs produce no candidates`() {
        assertTrue(
            LocalSubnetScanPlan.candidates("not-an-address", 24, null).isEmpty()
        )
        assertTrue(
            LocalSubnetScanPlan.candidates("192.168.1.5", 31, null).isEmpty()
        )
        assertFalse(
            LocalSubnetScanPlan.candidates(
                localAddress = "192.168.1.5",
                prefixLength = 24,
                previousHost = null,
                excluding = setOf("192.168.1.9")
            ).contains("192.168.1.9")
        )
    }
}
