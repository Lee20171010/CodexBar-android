package com.codexbar.android.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalNavigationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `notification dashboard pending intent targets MainActivity explicitly`() {
        val source = sourceFile("notification/QuotaNotificationService.kt")

        assertTrue(source.contains("Intent(context, MainActivity::class.java).apply"))
        assertTrue(source.contains("android.net.Uri.parse(\"codexbar://dashboard\")"))
        assertTrue(source.contains("PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE"))
        assertFalse(source.contains("val dashboardIntent = Intent().apply"))
        // The provider only ever travels as a query parameter on that same internal link.
        assertTrue(source.contains("appendQueryParameter(EXTRA_DASHBOARD_SERVICE, it.name)"))
    }

    @Test
    fun `quick settings setup navigation targets MainActivity explicitly`() {
        val source = sourceFile("tile/QuotaTileService.kt")

        assertTrue(source.contains("Intent(this, MainActivity::class.java).apply"))
        assertTrue(source.contains("data = android.net.Uri.parse(\"codexbar://dashboard\")"))
        assertTrue(source.contains("PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE"))
        assertFalse(source.contains("val intent = Intent().apply"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
