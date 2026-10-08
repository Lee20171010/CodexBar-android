package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.presentation.Freshness
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.updateQuotaSurfaces
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import kotlinx.coroutines.CancellationException

@HiltWorker
class QuotaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val accounts: AccountQuotaCoordinator,
    private val prefsManager: EncryptedPrefsManager,
    private val officialStatus: com.codexbar.android.core.data.OfficialStatusRepository,
    private val notificationService: QuotaNotificationService
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val manual = inputData.getBoolean("manual", false)
            if (!manual && prefsManager.getRefreshInterval() <= 0) return Result.success()
            val id = inputData.getString("connection")
            if (id == null) {
                WorkManagerInitializer.enqueueRefresh(applicationContext, manual)
                return Result.success()
            }
            val connection = prefsManager.loadConnections().find {
                it.id == id && it.generation == inputData.getString("generation")
            } ?: return Result.success()
            if (!manual && accounts.snapshot(connection).freshness() == Freshness.RECONNECT) {
                officialStatus.refresh(connection.service)
                return Result.success()
            }
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
            updateQuotaSurfaces(applicationContext)
            officialStatus.refresh(connection.service)
            if (result is com.codexbar.android.core.domain.model.Result.Failure && shouldRetry(result.error)) Result.retry()
            else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun shouldRetry(error: AppError): Boolean = when (error) {
            is AppError.AuthError -> !error.isTerminal
            is AppError.CredentialNotFound, is AppError.ParseError -> false
            else -> true
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(applicationContext, QuotaNotificationService.CHANNEL_ID)
            .setContentTitle("Refreshing quota data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        return ForegroundInfo(QuotaNotificationService.NOTIFICATION_ID + 1, notification)
    }
}
