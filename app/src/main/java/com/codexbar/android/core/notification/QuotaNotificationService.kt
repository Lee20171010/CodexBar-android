package com.codexbar.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.balanceText
import com.codexbar.android.core.presentation.Freshness
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.presentation.QuotaPresentation
import com.codexbar.android.core.workmanager.QuotaRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val now = Instant.now()
        val rows = notificationRows(context, quotas, now)
        val (visible, overflow) = expandedRows(context, rows)

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

        // Collapsed stays the standard system template (title + text). The expanded
        // area uses a decorated custom layout: DecoratedCustomViewStyle supplies the
        // frame, header and theming, so the content rows adapt to light/dark shades.
        // Decorated does NOT guarantee no clipping — device verification is required.
        val big = RemoteViews(context.packageName, R.layout.notification_expanded)
        val rowIds = listOf(R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5, R.id.row_6)
        val labelIds = listOf(R.id.row_label_1, R.id.row_label_2, R.id.row_label_3, R.id.row_label_4, R.id.row_label_5, R.id.row_label_6)
        val valueIds = listOf(R.id.row_value_1, R.id.row_value_2, R.id.row_value_3, R.id.row_value_4, R.id.row_value_5, R.id.row_value_6)
        visible.forEachIndexed { i, row ->
            big.setViewVisibility(rowIds[i], View.VISIBLE)
            big.setTextViewText(labelIds[i], row.label)
            big.setTextViewText(valueIds[i], row.value)
        }
        for (i in visible.size until MAX_EXPANDED_ROWS) big.setViewVisibility(rowIds[i], View.GONE)
        if (overflow != null) {
            big.setTextViewText(R.id.overflow_text, overflow)
            big.setViewVisibility(R.id.overflow_text, View.VISIBLE)
        } else {
            big.setViewVisibility(R.id.overflow_text, View.GONE)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_status_title))
            .setContentText("${rows.first().label} · ${rows.first().value}")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(big)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_refresh, context.getString(R.string.refresh), refreshPendingIntent)
        // The timer counts from the oldest trustworthy measurement (<= now); with only
        // missing or future timestamps there is no honest start, so no timer at all.
        // Rebuilds keep the same `when`: measurement time belongs to the reading, not
        // to the render. Per-row stale/reconnect markers keep mixed data honest.
        val oldest = oldestMeasurement(quotas, now)
        if (oldest != null) builder.setWhen(oldest).setShowWhen(true).setUsesChronometer(true)
        else builder.setShowWhen(false)

        manager.notify(NOTIFICATION_ID, builder.build())
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
            .setSmallIcon(R.drawable.ic_notification)
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
            .setSmallIcon(R.drawable.ic_notification)
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
}

internal data class NotificationRow(val label: String, val value: String)

// Expanded-area row cap: notification height adaptation for the shade, not Mac parity.
internal const val MAX_EXPANDED_ROWS = 6

// One row per connection. Label is the provider display name; duplicates are
// disambiguated by account name, and any identical final label gets a
// deterministic occurrence suffix.
internal fun notificationRows(
    context: Context,
    quotas: List<Pair<AccountConnection, QuotaSnapshot>>,
    now: Instant = Instant.now()
): List<NotificationRow> {
    val bases = quotas.map { it.first.service.displayName }
    val counts = bases.groupingBy { it }.eachCount()
    val raw = quotas.mapIndexed { i, (connection, _) ->
        val base = bases[i]
        if (counts.getValue(base) == 1 || connection.name == base) base else "$base (${connection.name})"
    }
    val dupes = raw.groupingBy { it }.eachCount()
    val seen = HashMap<String, Int>()
    val labels = raw.map { label ->
        if (dupes.getValue(label) == 1) label
        else {
            val n = (seen[label] ?: 0) + 1
            seen[label] = n
            "$label #$n"
        }
    }
    return quotas.mapIndexed { i, (_, snapshot) ->
        NotificationRow(labels[i], rowValue(context, snapshot, now))
    }
}

// Fresh rows show the window value with no age suffix; stale rows keep a concise
// marker (header owns age) and reconnect/unavailable rows their status, so old or
// failed data never looks fresh.
private fun rowValue(context: Context, snapshot: QuotaSnapshot, now: Instant): String =
    when (snapshot.freshness(now)) {
        Freshness.RECONNECT -> context.getString(R.string.reconnect_required)
        Freshness.UNAVAILABLE -> context.getString(R.string.quota_unavailable)
        Freshness.STALE -> {
            // No age (header timer owns it); numeric limit stays before the marker and
            // the value column ellipsizes from the start, keeping the marker visible.
            val marker = context.getString(R.string.stale_short)
            windowValue(context, snapshot.quota!!)?.let { "$it · $marker" } ?: marker
        }
        Freshness.CURRENT -> {
            val quota = snapshot.quota!!
            windowValue(context, quota)
                ?: quota.money?.balanceText()
                ?: context.getString(R.string.quota_unavailable)
        }
    }

// Explicit highest-utilization principal window: the service has no
// display-options wiring, so it does not claim to respect headline prefs.
private fun windowValue(context: Context, quota: QuotaInfo): String? =
    QuotaPresentation.principal(quota).maxByOrNull { it.utilization }?.let {
        "${it.label} ${context.getString(R.string.percent_left, QuotaPresentation.remainingPercent(it.utilization))}"
    }

internal fun expandedRows(context: Context, rows: List<NotificationRow>): Pair<List<NotificationRow>, String?> =
    if (rows.size <= MAX_EXPANDED_ROWS) rows to null
    else rows.take(MAX_EXPANDED_ROWS) to
        context.getString(R.string.widget_more_accounts, rows.size - MAX_EXPANDED_ROWS)

// Oldest trustworthy measurement (<= now) in epoch millis; null when every reading
// is missing or in the future, so the header timer is never started from bad data.
internal fun oldestMeasurement(
    quotas: List<Pair<AccountConnection, QuotaSnapshot>>,
    now: Instant = Instant.now()
): Long? = quotas
    .mapNotNull { it.second.quota?.fetchedAt }
    .filter { !it.isAfter(now) }
    .minOrNull()?.toEpochMilli()
