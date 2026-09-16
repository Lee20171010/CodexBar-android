package com.codexbar.android.di

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCredentialCacheSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `background surfaces resolve the injected credential cache first`() {
        val entryPoint = sourceFile("di/AppSingletonEntryPoint.kt")

        assertTrue(entryPoint.contains("@InstallIn(SingletonComponent::class)"))
        assertTrue(entryPoint.contains("fun encryptedPrefsManager(): EncryptedPrefsManager"))
        assertTrue(entryPoint.contains("fun widgetPrefsManager(): WidgetPrefsManager"))
        assertTrue(entryPoint.contains("fun appSingletonEntryPointOrNull(context: Context)"))
    }

    @Test
    fun `startup and widget rendering do not build their own credential cache`() {
        val initializer = sourceFile("core/workmanager/WorkManagerInitializer.kt")
        val widget = sourceFile("core/widget/QuotaGlanceWidget.kt")

        assertTrue(
            initializer.contains(
                "appSingletonEntryPointOrNull(appContext)?.encryptedPrefsManager()"
            )
        )
        assertTrue(widget.contains("appSingletonEntryPointOrNull(appContext)"))
        // Direct construction may only remain as the fallback when Hilt is not ready.
        assertEquals(1, initializer.occurrencesOf("EncryptedPrefsManager(appContext)"))
        assertEquals(1, widget.occurrencesOf("EncryptedPrefsManager(appContext)"))
        assertEquals(1, widget.occurrencesOf("WidgetPrefsManager(appContext)"))
    }

    private fun String.occurrencesOf(value: String): Int = split(value).size - 1

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
