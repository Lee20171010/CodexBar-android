package com.codexbar.android.core.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promoted ongoing entry is one line on One UI's Now Bar and on the Android 16 status chip, so
 * what it shows and where it leads is the whole feature.
 */
class NowBarContentSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val service: String = File(
        appDir,
        "src/main/java/com/codexbar/android/core/notification/QuotaNotificationService.kt"
    ).readText().replace("\r\n", "\n")

    @Test
    fun `the collapsed entry names the provider and its remaining quota`() {
        assertTrue(service.contains("R.string.notification_monitoring_headline"))
        val shortText = service.substring(service.indexOf("private fun shortCriticalText("))

        assertTrue(shortText.contains("primaryService?.primaryMetric?.remainingPercent"))
        assertTrue(shortText.contains("R.string.notification_live_remaining_short"))
        // Used percent is already the progress bar; the chip carries what is left.
        assertFalse(shortText.contains("usedPercent"))
    }

    @Test
    fun `expanding the entry summarizes every monitored provider`() {
        val overview = service.substring(
            service.indexOf("private fun monitoringOverview("),
            service.indexOf("fun showMonitoringPlaceholder(")
        )

        assertTrue(overview.contains("R.string.notification_overview_entry"))
        assertTrue(overview.contains("sortedByDescending"))
        assertTrue(overview.contains("MAX_OVERVIEW_SERVICES"))
        // Redaction has to win over the richer summary.
        assertTrue(overview.contains("if (privacySettings.notificationRedactionEnabled) return \"\""))
        assertTrue(service.contains("overview = overview"))
    }

    @Test
    fun `the progress track is split along the severity thresholds`() {
        val platform = service.substring(
            service.indexOf("private fun buildPlatformMonitoringNotification(")
        )

        assertTrue(platform.contains("Segment(WARNING_THRESHOLD_PERCENT)"))
        assertTrue(platform.contains("CRITICAL_THRESHOLD_PERCENT - WARNING_THRESHOLD_PERCENT"))
        assertTrue(platform.contains("Segment(100 - CRITICAL_THRESHOLD_PERCENT)"))
        assertTrue(platform.contains("setStyledByProgress(true)"))
        assertTrue(platform.contains("addProgressPoint("))
    }

    @Test
    fun `tapping the entry opens the provider it is about`() {
        assertTrue(service.contains("dashboardPendingIntent(primaryService?.service)"))
        assertTrue(service.contains("private fun dashboardPendingIntent(service: AiService? = null)"))
        assertTrue(service.contains("appendQueryParameter(EXTRA_DASHBOARD_SERVICE, it.name)"))
        // A per-provider link needs its own request code or it would reuse the generic intent.
        assertTrue(service.contains("service?.ordinal?.plus(1) ?: 0"))
    }

    @Test
    fun `pre Android 16 lock screen title does not reveal the provider or quota`() {
        val monitoring = service.substring(
            service.indexOf("fun showMonitoringNotification("),
            service.indexOf("private fun monitoringOverview(")
        )
        val privacy = monitoring.substring(monitoring.indexOf(".applyPrivacy("))

        assertTrue(privacy.contains(
            "redactedTitle = localizedString(R.string.notification_monitoring_title)"
        ))
        assertTrue(privacy.contains("redactedText = hiddenText"))
        assertFalse(privacy.contains("redactedTitle = title"))
    }

    @Test
    fun `the lock screen version still hides quota values`() {
        val platform = service.substring(
            service.indexOf("private fun buildPlatformMonitoringNotification(")
        )
        val publicVersion = platform.substring(platform.indexOf("val publicVersion ="))

        assertTrue(publicVersion.contains("R.string.notification_monitoring_title"))
        assertTrue(publicVersion.contains("R.string.notification_quota_hidden"))
    }
}
