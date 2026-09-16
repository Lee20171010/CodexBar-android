package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher keeps `widget_loading` on screen until a composition arrives, so every read that
 * runs before `provideContent` has to be both guarded and time bounded.
 */
class WidgetRenderResilienceSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val widget: String = File(
        appDir,
        "src/main/java/com/codexbar/android/core/widget/QuotaGlanceWidget.kt"
    ).readText().replace("\r\n", "\n")

    @Test
    fun `setup reads cannot abort the composition`() {
        val provideGlance = widget.substring(
            widget.indexOf("override suspend fun provideGlance("),
            widget.indexOf("private fun WidgetContent(")
        )

        assertTrue(provideGlance.contains("provideContent {"))
        assertTrue(provideGlance.contains("runCatching { GlanceAppWidgetManager(appContext).getAppWidgetId(id) }"))
        assertTrue(provideGlance.contains("AppWidgetManager.INVALID_APPWIDGET_ID"))
        assertTrue(provideGlance.contains("runCatching { dependencies.widgetPrefs.getWidgetConfig(appWidgetId) }"))
        assertTrue(provideGlance.contains("WidgetDisplayConfig()"))
        assertTrue(provideGlance.contains("runCatching { WidgetStrings("))
        // Every read must resolve before provideContent, never propagate out of provideGlance.
        assertEquals(0, provideGlance.occurrencesOf("throw "))
    }

    @Test
    fun `privacy settings are read behind a render deadline`() {
        val dependencies = widget.substring(widget.indexOf("internal class WidgetDependencies"))

        assertTrue(dependencies.contains("withTimeoutOrNull(SETTINGS_TIMEOUT_MILLIS)"))
        assertTrue(dependencies.contains("prefsManager.warmCache()"))
        assertTrue(dependencies.contains("prefsManager.getPrivacySettings().widgetRedactionEnabled"))
        // A settings read that fails still has to hide quota values.
        assertTrue(dependencies.contains(".getOrDefault(true)"))
        assertTrue(dependencies.contains("encryptedPrefs ?: return true"))
    }

    @Test
    fun `the empty state stays actionable`() {
        val emptyState = widget.substring(
            widget.indexOf("private fun EmptyState("),
            widget.indexOf("private fun RedactedState(")
        )

        assertTrue(emptyState.contains("strings.noServices"))
        assertTrue(emptyState.contains("strings.openDetails"))
    }

    private fun String.occurrencesOf(value: String): Int = split(value).size - 1
}
