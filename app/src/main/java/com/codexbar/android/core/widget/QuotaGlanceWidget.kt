package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.core.content.ContextCompat
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.di.appSingletonEntryPointOrNull
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.withTimeoutOrNull

class QuotaGlanceWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {

    // Exact composes only the launcher's current size. Responsive composes every declared
    // variant into one RemoteViews payload, which can exceed OEM launcher/Binder limits.
    override val sizeMode: SizeMode = SizeMode.Exact

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        Log.e(TAG, "Widget composition failed for id=$appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /**
     * Nothing here may throw or block indefinitely. The launcher keeps showing
     * `widget_loading` until [provideContent] returns a composition, so a failed or slow setup
     * read has to degrade into a rendered state instead of aborting the update.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val dependencies = WidgetDependencies.of(appContext)
        val appWidgetId = runCatching { GlanceAppWidgetManager(appContext).getAppWidgetId(id) }
            .getOrElse { error ->
                Log.w(TAG, "Could not resolve the App Widget ID for $id", error)
                AppWidgetManager.INVALID_APPWIDGET_ID
            }
        val config = runCatching { dependencies.widgetPrefs.getWidgetConfig(appWidgetId) }
            .getOrElse { error ->
                Log.e(TAG, "Could not read the configuration for id=$appWidgetId", error)
                WidgetDisplayConfig()
            }
        val redactQuotaDetails = dependencies.readWidgetRedaction()
        val strings = runCatching { WidgetStrings(ContextCompat.getContextForLanguage(context)) }
            .getOrElse { WidgetStrings(context) }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    config = config,
                    widgetPrefs = dependencies.widgetPrefs,
                    redactQuotaDetails = redactQuotaDetails,
                    strings = strings
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        config: WidgetDisplayConfig,
        widgetPrefs: WidgetPrefsManager,
        redactQuotaDetails: Boolean,
        strings: WidgetStrings
    ) {
        val size = LocalSize.current
        val selectedServices = config.services
        val heightDp = size.height.value.toInt()
        val maxServices = WidgetRenderPolicy.maxServices(heightDp)
        val maxRows = WidgetRenderPolicy.maxRows(heightDp, config.maxRows)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(ColorProvider(Color(0xB01C1B1F)))
                .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp)
        ) {
            if (redactQuotaDetails) {
                RedactedState(strings)
            } else if (selectedServices.isEmpty()) {
                EmptyState(strings)
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    for ((index, service) in selectedServices.take(maxServices).withIndex()) {
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Divider()
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                        ServiceSection(
                            service = service,
                            widgetPrefs = widgetPrefs,
                            config = config.copy(maxRows = maxRows),
                            showRefresh = index == 0,
                            strings = strings
                        )
                    }
                    if (selectedServices.size > maxServices) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = strings.moreServices(selectedServices.size - maxServices),
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.45f)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(strings: WidgetStrings) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings.noServices,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = strings.openDetails,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.55f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun RedactedState(strings: WidgetStrings) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings.quotaHidden,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = strings.openDetails,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.55f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun Divider() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(Color.White.copy(alpha = 0.1f)))
        ) {}
    }

    @Composable
    private fun ServiceSection(
        service: AiService,
        widgetPrefs: WidgetPrefsManager,
        config: WidgetDisplayConfig,
        showRefresh: Boolean,
        strings: WidgetStrings
    ) {
        val labels = widgetPrefs.getCachedLabels(service).take(config.maxRows)
        val tier = widgetPrefs.getCachedTier(service)
        val freshness = widgetPrefs.getCachedFreshness(service)
        val statusMessage = widgetPrefs.getCachedStatusMessage(service)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Header: service name + tier + refresh button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Service icon dot
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(ColorProvider(Color(service.brandColor)))
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = service.displayName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (tier != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(4.dp)
                            .background(ColorProvider(Color.White.copy(alpha = 0.15f)))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tier,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (showRefresh) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = strings.refreshDescription,
                        modifier = GlanceModifier
                            .size(18.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>()),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White.copy(alpha = 0.5f)))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (config.showFreshness && freshness != null) {
                Text(
                    text = strings.updated(freshness),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.45f)),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }

            // Each usage window — same layout as app dashboard
            for ((index, label) in labels.withIndex()) {
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                WindowRow(service, label, widgetPrefs, config)
            }

            // Show placeholder if no cached data yet
            if (labels.isEmpty()) {
                Text(
                    text = statusMessage ?: strings.waitingForData,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun WindowRow(
        service: AiService,
        label: String,
        widgetPrefs: WidgetPrefsManager,
        config: WidgetDisplayConfig
    ) {
        val utilization = widgetPrefs.getCachedUtilization(service, label)
        val barProgress = widgetPrefs.getCachedBarProgress(service, label)
        val remainingLabel = widgetPrefs.getCachedRemainingLabel(service, label)
        val resetText = widgetPrefs.getCachedResetLabel(service, label).orEmpty()
        val paceText = widgetPrefs.getCachedPaceLabel(service, label).orEmpty()
        val resetPlanText = widgetPrefs.getCachedResetPlanLabel(service, label).orEmpty()
        val severity = widgetPrefs.getCachedSeverity(service, label)
            ?.let { runCatching { QuotaSeverity.valueOf(it) }.getOrNull() }
            ?: severityForUtilization(utilization)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Label + percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        color = severityColor(severity),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Progress bar
            QuotaProgressBar(barProgress, severity)

            // Reset time
            val detailText = listOf(
                resetText.takeIf { config.showReset },
                resetPlanText.ifBlank { paceText }.takeIf { config.showPace }
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            if (detailText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = detailText,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                            fontSize = 10.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }

    @Composable
    private fun QuotaProgressBar(barProgress: Float, severity: QuotaSeverity) {
        LinearProgressIndicator(
            progress = barProgress.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = severityColor(severity),
            backgroundColor = ColorProvider(Color.White.copy(alpha = 0.1f))
        )
    }

    companion object {
        private const val TAG = "CodexBarWidget"

        fun utilizationColor(utilization: Float): ColorProvider {
            return severityColor(severityForUtilization(utilization))
        }

        fun severityForUtilization(utilization: Float): QuotaSeverity {
            return when {
                utilization >= 0.85f -> QuotaSeverity.Critical
                utilization >= 0.60f -> QuotaSeverity.Warning
                else -> QuotaSeverity.Good
            }
        }

        fun severityColor(severity: QuotaSeverity): ColorProvider {
            val color = when {
                severity == QuotaSeverity.Critical -> Color(0xFFEF5350)
                severity == QuotaSeverity.Warning -> Color(0xFFFFB74D)
                severity == QuotaSeverity.Redacted -> Color.White.copy(alpha = 0.35f)
                severity == QuotaSeverity.Unknown -> Color.White.copy(alpha = 0.45f)
                else -> Color(0xFF81C784)
            }
            return ColorProvider(color)
        }

        fun formatResetTime(epochSecond: Long): String {
            val now = Instant.now()
            val resetAt = Instant.ofEpochSecond(epochSecond)
            if (resetAt.isBefore(now)) return ""
            val duration = Duration.between(now, resetAt)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            return when {
                hours >= 24 -> "${hours / 24}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }
    }
}

/**
 * Resolves the application-scoped managers the widget renders from.
 *
 * Constructing them per render re-ran the legacy-preferences migration and warmed a second
 * credential cache on every launcher update, which is exactly the work that can outlast a
 * broadcast. Hilt already owns one instance of each; direct construction stays as a fallback for
 * hosts that update the widget before the application component exists.
 */
internal class WidgetDependencies private constructor(
    val widgetPrefs: WidgetPrefsManager,
    private val encryptedPrefs: EncryptedPrefsManager?
) {
    /**
     * Returns whether quota values must stay hidden. A failed or slow read keeps the
     * fail-closed default from [EncryptedPrefsManager] rather than blocking the composition.
     */
    suspend fun readWidgetRedaction(): Boolean {
        val prefsManager = encryptedPrefs ?: return true
        val warmed = runCatching {
            withTimeoutOrNull(SETTINGS_TIMEOUT_MILLIS) { prefsManager.warmCache() }
        }.getOrNull()
        if (warmed == null) {
            Log.w(TAG, "Widget privacy settings were unavailable within the render deadline")
        }
        return runCatching { prefsManager.getPrivacySettings().widgetRedactionEnabled }
            .getOrDefault(true)
    }

    companion object {
        private const val TAG = "CodexBarWidget"
        private const val SETTINGS_TIMEOUT_MILLIS = 2_000L

        fun of(appContext: Context): WidgetDependencies {
            val entryPoint = appSingletonEntryPointOrNull(appContext)
            val widgetPrefs = runCatching { entryPoint?.widgetPrefsManager() }.getOrNull()
                ?: WidgetPrefsManager(appContext)
            val prefsManager = runCatching { entryPoint?.encryptedPrefsManager() }.getOrNull()
                ?: runCatching { EncryptedPrefsManager(appContext) }.getOrNull()
            return WidgetDependencies(widgetPrefs, prefsManager)
        }
    }
}

private class WidgetStrings(private val context: Context) {
    val noServices: String = context.getString(R.string.widget_no_services)
    val quotaHidden: String = context.getString(R.string.widget_quota_hidden)
    val openDetails: String = context.getString(R.string.widget_open_details)
    val waitingForData: String = context.getString(R.string.widget_waiting_for_data)
    val refreshDescription: String = context.getString(R.string.widget_refresh_description)

    fun moreServices(count: Int): String = context.getString(R.string.widget_more_services, count)

    fun updated(freshness: String): String = context.getString(R.string.widget_updated, freshness)
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WorkManagerInitializer.enqueueManualQuotaRefresh(context, source = "widget")
        QuotaGlanceWidget().update(context, glanceId)
    }
}
