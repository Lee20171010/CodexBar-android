package com.codexbar.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.QuotaInfo
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
        const val NOTIFICATION_ID = 1001
        const val RESET_NOTIFICATION_ID_BASE = 2000
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

        manager.createNotificationChannels(listOf(monitorChannel, resetChannel))
    }

    fun showQuotaNotification(quotas: List<Pair<AccountConnection, QuotaSnapshot>>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (quotas.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_compact)

        // Populate service data
        quotas.forEachIndexed { index, (connection, snapshot) ->
            if (index >= 3) return@forEachIndexed // Max 3 services

            val maxUtilization = snapshot.quota?.let(QuotaPresentation::principal)?.maxOfOrNull { it.utilization }
            val progress = maxUtilization?.let(QuotaPresentation::remainingPercent) ?: 0
            val value = QuotaPresentation.summary(snapshot)
            val barId = listOf(R.id.progress_bar_1, R.id.progress_bar_2, R.id.progress_bar_3)[index]
            remoteViews.setViewVisibility(barId, if (maxUtilization == null) android.view.View.GONE else android.view.View.VISIBLE)

            when (index) {
                0 -> {
                    remoteViews.setTextViewText(R.id.service_name_1, connection.name)
                    remoteViews.setProgressBar(R.id.progress_bar_1, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_1, value)
                }
                1 -> {
                    remoteViews.setTextViewText(R.id.service_name_2, connection.name)
                    remoteViews.setProgressBar(R.id.progress_bar_2, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_2, value)
                }
                2 -> {
                    remoteViews.setTextViewText(R.id.service_name_3, connection.name)
                    remoteViews.setProgressBar(R.id.progress_bar_3, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_3, value)
                }
            }
        }

        remoteViews.setTextViewText(R.id.update_time, QuotaPresentation.age(quotas.mapNotNull { it.second.quota?.fetchedAt }.minOrNull()))

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setCustomContentView(remoteViews)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showResetNotification(connection: AccountConnection, windowLabel: String) {
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
            .setContentTitle("${connection.name} quota reset")
            .setContentText("$windowLabel window has been reset. Your quota is fully available.")
            .setContentIntent(dashboardPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify("${connection.id}:$windowLabel", RESET_NOTIFICATION_ID_BASE, notification)
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
