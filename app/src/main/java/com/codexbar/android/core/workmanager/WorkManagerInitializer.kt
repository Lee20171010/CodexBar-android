package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codexbar.android.core.monitoring.MonitoringSession
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.QuotaGlanceWidget
import com.codexbar.android.di.appSingletonEntryPointOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WorkManagerInitializer private constructor() {
    companion object {
        private const val QUOTA_WORK_NAME = "quota_periodic_refresh"
        private const val TOKEN_WORK_NAME = "token_periodic_refresh"
        private const val MANUAL_QUOTA_WORK_NAME = "quota_manual_refresh"
        private const val MONITORING_QUOTA_WORK_NAME = "quota_monitoring_refresh"
        private const val WIDGET_RENDER_WORK_NAME_PREFIX = "widget_render_"
        const val KEY_REFRESH_SOURCE = "refresh_source"

        fun applyRefreshPolicy(context: Context, intervalMinutes: Long) {
            val normalizedInterval = RefreshIntervalPolicy.normalize(intervalMinutes)
            schedulePeriodicRefresh(context, normalizedInterval)
            scheduleTokenRefresh(context, normalizedInterval)
        }

        fun applySavedRefreshPolicyAsync(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                // The injected singleton is the same cache every other surface reads, so warming
                // it here also primes the widget and the notification renderers.
                val prefsManager = appSingletonEntryPointOrNull(appContext)?.encryptedPrefsManager()
                    ?: EncryptedPrefsManager(appContext)
                prefsManager.warmCache()
                applyRefreshPolicy(appContext, prefsManager.getRefreshInterval())
                QuotaGlanceWidget().updateAll(appContext)
            }
        }

        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(QUOTA_WORK_NAME)
                return
            }

            // WorkManager minimum is 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<QuotaRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("quota_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QUOTA_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun scheduleTokenRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(TOKEN_WORK_NAME)
                return
            }

            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("token_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TOKEN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueManualQuotaRefresh(context: Context, source: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_REFRESH_SOURCE to source))
                .addTag("quota_refresh")
                .addTag("quota_refresh_manual")
                .addTag("quota_refresh_source_$source")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_QUOTA_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueWidgetRender(
            context: Context,
            appWidgetId: Int,
            delayMillis: Long = 1_200L
        ) {
            if (appWidgetId == WidgetRenderWorker.INVALID_APP_WIDGET_ID) return

            val request = OneTimeWorkRequestBuilder<WidgetRenderWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(WidgetRenderWorker.KEY_APP_WIDGET_ID to appWidgetId))
                .addTag("widget_render")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$WIDGET_RENDER_WORK_NAME_PREFIX$appWidgetId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun startMonitoringSession(
            context: Context,
            durationMinutes: Long = MonitoringSessionStore.DEFAULT_DURATION_MINUTES,
            cadenceMinutes: Long = MonitoringSessionStore.DEFAULT_CADENCE_MINUTES
        ): MonitoringSession {
            val session = MonitoringSessionStore(context.applicationContext)
                .start(durationMinutes = durationMinutes, cadenceMinutes = cadenceMinutes)
            enqueueManualQuotaRefresh(context, source = "monitoring_start")
            return session
        }

        fun stopMonitoringSession(context: Context) {
            val appContext = context.applicationContext
            MonitoringSessionStore(appContext).stop()
            WorkManager.getInstance(appContext).cancelUniqueWork(MONITORING_QUOTA_WORK_NAME)
        }

        fun scheduleNextMonitoringRefresh(context: Context) {
            val appContext = context.applicationContext
            val session = MonitoringSessionStore(appContext).activeSession() ?: return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .setInitialDelay(session.cadenceMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_REFRESH_SOURCE to "monitoring"))
                .addTag("quota_refresh")
                .addTag("quota_refresh_monitoring")
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                MONITORING_QUOTA_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
