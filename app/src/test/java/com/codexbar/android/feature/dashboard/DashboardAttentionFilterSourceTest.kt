package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The needs-attention chips only exist while something is failing, so the saved selection must
 * not survive the recovery that removes them.
 */
class DashboardAttentionFilterSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val dashboard: String = File(
        appDir,
        "src/main/java/com/codexbar/android/feature/dashboard/DashboardScreen.kt"
    ).readText().replace("\r\n", "\n")

    @Test
    fun `a stale attention filter cannot empty the list`() {
        assertTrue(
            dashboard.contains(
                "val filterAttention = showOnlyAttention && summary.attentionCount > 0"
            )
        )
        assertTrue(dashboard.contains("if (filterAttention) {"))
        // The chips report the effective filter, never the stale saved flag.
        assertFalse(dashboard.contains("showOnlyAttention = showOnlyAttention,"))
        assertTrue(dashboard.contains("showOnlyAttention = filterAttention,"))
    }

    @Test
    fun `the filter row only appears while a provider is failing`() {
        val cardList = dashboard.substring(dashboard.indexOf("private fun CardList("))

        assertTrue(cardList.contains("if (attentionCount > 0) {"))
        assertTrue(cardList.contains("R.string.dashboard_filter_all"))
        assertTrue(cardList.contains("R.string.dashboard_filter_attention"))
    }
}
