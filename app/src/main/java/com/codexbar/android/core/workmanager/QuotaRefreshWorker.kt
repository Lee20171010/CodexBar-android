package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.updateQuotaSurfaces
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class QuotaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val accounts: AccountQuotaCoordinator,
    private val prefsManager: EncryptedPrefsManager,
    private val notificationService: QuotaNotificationService
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = try {
        val connections = prefsManager.loadConnections()
        val results = coroutineScope {
            connections.map { connection -> async {
                val result = accounts.refresh(connection)
                if (result is com.codexbar.android.core.domain.model.Result.Success) {
                    prefsManager.publishIfCurrent(connection) {
                        if (prefsManager.isNotificationsEnabled()) {
                            val previous = prefsManager.loadResetTimes(connection)
                            val now = Instant.now()
                            result.value.windows.forEach { window ->
                                val old = previous[window.label]
                                if (old != null && old.isBefore(now) && window.resetsAt?.isAfter(now) == true) {
                                    notificationService.showResetNotification(connection, window.label)
                                }
                            }
                            prefsManager.saveResetTimes(connection, result.value.windows.map { it.label to it.resetsAt })
                        }
                    }
                }
                result
            } }.awaitAll()
        }
        updateQuotaSurfaces(applicationContext)
        if (connections.isEmpty() || results.any { it is com.codexbar.android.core.domain.model.Result.Success }) Result.success()
        else Result.retry()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(applicationContext, QuotaNotificationService.CHANNEL_ID)
            .setContentTitle("Refreshing quota data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        return ForegroundInfo(QuotaNotificationService.NOTIFICATION_ID + 1, notification)
    }
}
