package com.codexbar.android.core.workmanager

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.data.QuotaRepositoryRegistry
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.presentation.AndroidQuotaPresentationText
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import com.codexbar.android.core.presentation.RefreshSourcePresentation
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.tile.QuotaTileService
import com.codexbar.android.core.widget.QuotaGlanceWidget
import com.codexbar.android.core.widget.QuotaWidgetReceiver
import com.codexbar.android.core.widget.WidgetPrefsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

@HiltWorker
class QuotaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repositoryRegistry: QuotaRepositoryRegistry,
    private val prefsManager: EncryptedPrefsManager,
    private val connectionHealthStore: ConnectionHealthStore,
    private val notificationService: QuotaNotificationService,
    private val widgetPrefsManager: WidgetPrefsManager,
    private val quotaHistoryStore: QuotaHistoryStore,
    private val monitoringSessionStore: MonitoringSessionStore
) : CoroutineWorker(context, workerParams) {

    private val presentationMapper = QuotaPresentationMapper(
        text = AndroidQuotaPresentationText(context)
    )

    override suspend fun doWork(): Result {
        prefsManager.warmCache()
        val repos = repositoryRegistry.entries().mapNotNull { (service, repository) ->
            if (prefsManager.loadCredential(service) == null) return@mapNotNull null
            service to repository
        }

        if (repos.isEmpty()) {
            // A widget can outlive its selected account. Always replace the provider's
            // initial loading layout even when there is no network work to perform.
            val languageContext = ContextCompat.getContextForLanguage(applicationContext)
            AiService.entries.forEach { service ->
                widgetPrefsManager.cacheStatusMessageIfEmpty(
                    service,
                    languageContext.getString(R.string.widget_not_connected)
                )
            }
            try {
                QuotaGlanceWidget().updateAll(applicationContext)
            } catch (error: Exception) {
                // Widget rendering cannot turn a no-op account refresh into retry work.
                Log.e(TAG, "Widget render failed after disconnected refresh", error)
            }
            return Result.success()
        }

        return try {
            val refreshResults = coroutineScope {
                repos.map { (service, repo) ->
                    async { service to repo.fetchQuota() }
                }.awaitAll()
            }

            val successfulQuotas = mutableListOf<QuotaInfo>()
            val errors = mutableMapOf<AiService, AppError>()
            for ((service, result) in refreshResults) {
                connectionHealthStore.record(service, result)
                when (result) {
                    is com.codexbar.android.core.domain.model.Result.Success -> {
                        successfulQuotas.add(result.value)
                    }
                    is com.codexbar.android.core.domain.model.Result.Failure -> {
                        errors[service] = result.error
                    }
                }
            }

            val privacySettings = prefsManager.getPrivacySettings()
            val now = Instant.now()
            val paceByMetricKey = if (successfulQuotas.isNotEmpty()) {
                quotaHistoryStore.record(successfulQuotas)
                quotaHistoryStore.paceFor(successfulQuotas, now)
            } else {
                emptyMap()
            }
            val snapshot = presentationMapper.map(
                quotas = successfulQuotas,
                errors = errors,
                generatedAt = now,
                privacy = PrivacyPresentation(
                    redactSensitiveValues = false,
                    lockScreenRedacted = privacySettings.lockScreenRedactionEnabled,
                    widgetRedacted = privacySettings.widgetRedactionEnabled
                ),
                source = RefreshSourcePresentation.Trigger(
                    inputData.getString(WorkManagerInitializer.KEY_REFRESH_SOURCE) ?: "periodic"
                ),
                paceByMetricKey = paceByMetricKey
            )

            // Publish both successful data and actionable provider errors to widgets.
            cacheQuotaData(snapshot)
            QuotaGlanceWidget().updateAll(applicationContext)

            notificationService.publishSnapshot(
                snapshot = snapshot,
                monitoringSession = monitoringSessionStore.activeSession()
            )

            if (
                successfulQuotas.isNotEmpty() &&
                prefsManager.isPersistentNotificationEnabled()
            ) {
                checkForResets(successfulQuotas)
            }

            // Request tile update
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, QuotaTileService::class.java)
            )

            WorkManagerInitializer.scheduleNextMonitoringRefresh(applicationContext)

            if (successfulQuotas.isEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (error: Exception) {
            val languageContext = ContextCompat.getContextForLanguage(applicationContext)
            repos.forEach { (service, _) ->
                widgetPrefsManager.cacheStatusMessageIfEmpty(
                    service,
                    languageContext.getString(R.string.widget_refresh_failed)
                )
            }
            runCatching { QuotaGlanceWidget().updateAll(applicationContext) }
                .onFailure { renderError ->
                    Log.e(TAG, "Widget render failed after refresh error", renderError)
                }
            Log.e(TAG, "Quota refresh worker failed", error)
            Result.retry()
        }
    }

    private fun cacheQuotaData(snapshot: QuotaPresentationSnapshot) {
        for (service in snapshot.services) {
            widgetPrefsManager.cachePresentation(service)
        }
    }

    private suspend fun checkForResets(quotas: List<QuotaInfo>) {
        val now = Instant.now()
        for (quota in quotas) {
            val previousResetTimes = prefsManager.loadResetTimes(quota.service)

            // Detect resets: previous resetsAt was in the future, now it's in the past
            for (window in quota.windows) {
                val previousResetAt = previousResetTimes[window.label] ?: continue
                if (previousResetAt.isBefore(now) && window.resetsAt != null && window.resetsAt.isAfter(now)) {
                    notificationService.showResetNotification(quota.service, window.label)
                }
            }

            // Save current reset times for next comparison
            prefsManager.saveResetTimes(
                quota.service,
                quota.windows.map { it.label to it.resetsAt }
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val languageContext = ContextCompat.getContextForLanguage(applicationContext)
        val notification = android.app.Notification.Builder(applicationContext, QuotaNotificationService.CHANNEL_ID)
            .setContentTitle(languageContext.getString(R.string.worker_refreshing_quota))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        return ForegroundInfo(QuotaNotificationService.NOTIFICATION_ID + 1, notification)
    }

    companion object {
        private const val TAG = "CodexBarRefresh"
    }
}
