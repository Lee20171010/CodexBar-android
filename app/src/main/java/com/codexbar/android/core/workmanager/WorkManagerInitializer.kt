package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.work.*
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

object WorkManagerInitializer {
    private const val QUOTA_WORK_NAME = "quota_periodic_refresh"
    private const val TOKEN_WORK_NAME = "token_periodic_refresh"
    const val AUTO_TAG = "quota_automatic"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies { fun prefs(): EncryptedPrefsManager }

    private fun prefs(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).prefs()

    fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = prefs(context).getRefreshInterval()) {
        val manager = WorkManager.getInstance(context)
        scheduleTokenRefresh(context)
        if (intervalMinutes <= 0) {
            manager.cancelUniqueWork(QUOTA_WORK_NAME)
            manager.cancelAllWorkByTag(AUTO_TAG)
            return
        }
        val request = PeriodicWorkRequestBuilder<QuotaRefreshWorker>(intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(network())
            .addTag(AUTO_TAG)
            .build()
        manager.enqueueUniquePeriodicWork(QUOTA_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun enqueueRefresh(context: Context, manual: Boolean, connections: List<AccountConnection> = prefs(context).loadConnections()) {
        val manager = WorkManager.getInstance(context)
        connections.forEach { connection ->
            val request = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .setInputData(workDataOf("connection" to connection.id, "generation" to connection.generation, "manual" to manual))
                .setConstraints(network())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag(if (manual) "quota_refresh_manual" else AUTO_TAG)
                .build()
            // Manual work survives Manual mode; coordinator coalesces simultaneous owners.
            manager.enqueueUniqueWork("quota_${if (manual) "manual" else "auto"}_${connection.id}_${connection.generation}",
                ExistingWorkPolicy.KEEP, request)
        }
    }

    fun scheduleTokenRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TOKEN_WORK_NAME)
    }

    private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
