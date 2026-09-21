package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataSynchronizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard refresh writes and renders the same widget snapshot`() {
        val dashboard = sourceFile("feature/dashboard/DashboardViewModel.kt")
        val publishStart = dashboard.indexOf("private suspend fun publishSnapshot(")
        assertTrue("publishSnapshot must exist", publishStart >= 0)
        val publish = dashboard.substring(publishStart)

        assertTrue(publish.contains("snapshot.services.forEach(widgetPrefsManager::cachePresentation)"))
        assertTrue(publish.contains("QuotaGlanceWidget().updateAll(appContext)"))
    }

    @Test
    fun `worker renders widgets even when no account remains`() {
        val worker = sourceFile("core/workmanager/QuotaRefreshWorker.kt")
        val emptyRepos = worker.substringAfter("if (repos.isEmpty()) {")
            .substringBefore("return Result.success()")

        assertTrue(emptyRepos.contains("QuotaGlanceWidget().updateAll(applicationContext)"))
    }

    @Test
    fun `metricless successful providers replace the waiting placeholder`() {
        val prefs = sourceFile("core/widget/WidgetPrefsManager.kt")
        val cachePresentation = prefs.substringAfter("fun cachePresentation(")
            .substringBefore("private fun SharedPreferences.Editor.cacheMetric")

        assertTrue(cachePresentation.contains("if (service.metrics.isEmpty())"))
        assertTrue(cachePresentation.contains("widget_no_bounded_quota"))
        assertTrue(cachePresentation.contains("_status_message"))
    }

    @Test
    fun `balance-only providers cache their balance after any stale reason`() {
        val prefs = sourceFile("core/widget/WidgetPrefsManager.kt")
        val cachePresentation = prefs.substringAfter("fun cachePresentation(")
            .substringBefore("private fun SharedPreferences.Editor.cacheMetric")
        val staleReason = cachePresentation.indexOf("service.freshness.staleReason")
        val balance = cachePresentation.indexOf("service.balance?.let", startIndex = staleReason)

        assertTrue(staleReason >= 0)
        assertTrue(balance > staleReason)
        assertTrue(cachePresentation.contains("it.label"))
        assertTrue(cachePresentation.contains("it.amountLabel"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
