package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placeholder layouts are the only thing on screen while Glance has not rendered, so they must
 * stay reachable instead of stranding the user on an inert tile.
 */
class WidgetPlaceholderRecoverySourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `placeholder layouts expose a clickable root`() {
        val loading = layout("widget_loading.xml")
        val error = layout("widget_error.xml")

        assertTrue(loading.contains("android:id=\"@+id/widget_loading_root\""))
        assertTrue(error.contains("android:id=\"@+id/widget_error_root\""))
    }

    @Test
    fun `an exhausted render replaces the loading layout with a tappable fallback`() {
        val worker = File(
            appDir,
            "src/main/java/com/codexbar/android/core/workmanager/WidgetRenderWorker.kt"
        ).readText().replace("\r\n", "\n")
        val fallback = worker.substring(worker.indexOf("private fun publishUnrenderableState("))

        assertTrue(worker.contains("publishUnrenderableState(appWidgetId)"))
        assertTrue(worker.contains("if (runAttemptCount < MAX_RETRY_COUNT) {"))
        assertTrue(fallback.contains("R.layout.widget_error"))
        assertTrue(fallback.contains("setOnClickPendingIntent(\n                    R.id.widget_error_root,"))
        assertTrue(fallback.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertTrue(fallback.contains("updateAppWidget(appWidgetId, views)"))
    }

    @Test
    fun `the configuration placeholder opens the app while the first render is pending`() {
        val activity = File(
            appDir,
            "src/main/java/com/codexbar/android/core/widget/WidgetConfigurationActivity.kt"
        ).readText().replace("\r\n", "\n")
        val confirmSelection = activity.substring(activity.indexOf("private fun confirmSelection("))

        assertTrue(confirmSelection.contains("R.id.widget_loading_root"))
        assertTrue(confirmSelection.contains("PendingIntent.FLAG_IMMUTABLE"))
    }

    private fun layout(name: String): String {
        return File(appDir, "src/main/res/layout/$name").readText().replace("\r\n", "\n")
    }
}
