package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A home screen widget sits on whatever surface the launcher provides, so nothing it draws may
 * assume a dark background.
 */
class WidgetThemeSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val widget: String = File(
        appDir,
        "src/main/java/com/codexbar/android/core/widget/QuotaGlanceWidget.kt"
    ).readText().replace("\r\n", "\n")

    @Test
    fun `the widget draws from the launcher theme`() {
        assertTrue(widget.contains("GlanceTheme.colors.widgetBackground"))
        assertTrue(widget.contains("GlanceTheme.colors.onSurface"))
        assertTrue(widget.contains("GlanceTheme.colors.onSurfaceVariant"))
        // The only remaining literal color is the provider's own brand dot.
        assertEquals(1, widget.split("Color(service.brandColor)").size - 1)
        assertFalse(widget.contains("Color.White"))
        assertFalse(widget.contains("0xB01C1B1F"))
    }

    @Test
    fun `severity colors have a night variant`() {
        val day = colors("values")
        val night = colors("values-night")
        val names = listOf(
            "widget_severity_good",
            "widget_severity_warning",
            "widget_severity_critical",
            "widget_severity_unknown",
            "widget_placeholder_background",
            "widget_placeholder_text"
        )

        names.forEach { name ->
            assertTrue("$name is missing a light value", day.contains("name=\"$name\""))
            assertTrue("$name is missing a dark value", night.contains("name=\"$name\""))
        }
        assertTrue(widget.contains("R.color.widget_severity_critical"))
        assertTrue(widget.contains("R.color.widget_severity_good"))
    }

    @Test
    fun `placeholder layouts follow the same colors`() {
        listOf("widget_loading.xml", "widget_error.xml").forEach { name ->
            val layout = File(appDir, "src/main/res/layout/$name")
                .readText()
                .replace("\r\n", "\n")
            assertTrue(layout.contains("@color/widget_placeholder_background"))
            assertTrue(layout.contains("@color/widget_placeholder_text"))
            assertFalse(layout.contains("#B01C1B1F"))
        }
    }

    private fun colors(qualifier: String): String {
        return File(appDir, "src/main/res/$qualifier/colors.xml").readText().replace("\r\n", "\n")
    }
}
