package com.codexbar.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.presentation.QuotaPresentation
import com.codexbar.android.core.workmanager.QuotaRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "quota_monitor"
        const val RESET_CHANNEL_ID = "quota_reset_alert"
        const val WARNING_CHANNEL_ID = "quota_warning_alert"
        const val NOTIFICATION_ID = 1001
        const val RESET_NOTIFICATION_ID_BASE = 2000
        const val WARNING_NOTIFICATION_ID_BASE = 3000
        const val ACTION_REFRESH = "com.codexbar.android.ACTION_REFRESH"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "AI Quota Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current AI service quota usage"
            setShowBadge(false)
        }

        val resetChannel = NotificationChannel(
            RESET_CHANNEL_ID,
            "Quota Reset Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when AI service quotas have been reset"
        }

        val warningChannel = NotificationChannel(
            WARNING_CHANNEL_ID,
            "Quota Usage Warnings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when quotas are low, depleted, or pacing to run out early"
        }

        manager.createNotificationChannels(listOf(monitorChannel, resetChannel, warningChannel))
    }

    fun showQuotaNotification(quotas: List<Pair<AccountConnection, QuotaSnapshot>>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (quotas.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val lines = quotaSummaryLines(context, quotas)

        // Refresh action
        val refreshIntent = Intent(context, RefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dashboard tap intent
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Standard system templates instead of a custom RemoteViews: OEM shades clip and
        // re-theme custom views (collapsed second row lost, expanded content dropped).
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(context.getString(R.string.notification_status_title))
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_refresh, context.getString(R.string.refresh), refreshPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showResetNotification(connection: AccountConnection, windowLabel: String, remainingPercent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RESET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(context.getString(R.string.recovery_title, connection.name))
            .setContentText(context.getString(R.string.recovery_detail, windowLabel, remainingPercent))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(dashboardPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify("${connection.id}:$windowLabel", RESET_NOTIFICATION_ID_BASE, notification)
    }

    fun showUsageAlert(connection: AccountConnection, kind: QuotaAlertKind, windowLabel: String, remainingPercent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val titleRes = when (kind) {
            QuotaAlertKind.DEPLETED -> R.string.alert_depleted_title
            QuotaAlertKind.LOW -> R.string.alert_low_title
            QuotaAlertKind.PACE -> R.string.alert_pace_title
        }
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(context.getString(titleRes, connection.name))
            .setContentText(context.getString(R.string.alert_detail, windowLabel, remainingPercent))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(dashboardPendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify("${connection.id}:$windowLabel:${kind.name}", WARNING_NOTIFICATION_ID_BASE, notification)
    }

    fun clearAccount(connection: AccountConnection) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.filter { it.tag?.startsWith("${connection.id}:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun formatElapsed(fetchedAt: Instant?): String {
        if (fetchedAt == null) return "just now"
        val elapsed = Duration.between(fetchedAt, Instant.now())
        return when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} min ago"
            else -> "${elapsed.toHours()}h ago"
        }
    }
}

// One line per connection: "Name · 5-Hour 65% left · just now". The collapsed
// notification shows the first line; the expanded InboxStyle shows every line.
internal fun quotaSummaryLines(
    context: Context,
    quotas: List<Pair<AccountConnection, QuotaSnapshot>>
): List<String> = quotas.map { (connection, snapshot) ->
    "${connection.name} · ${QuotaPresentation.summary(context, snapshot)}"
}
