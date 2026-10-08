package com.codexbar.android

import android.content.Context
import androidx.work.WorkManager
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Fresh, isolated acceptance install only. No credentials or account network requests. */
internal suspend fun verifyScheduling(context: Context): Boolean = withContext(Dispatchers.IO) {
    val prefs = EncryptedPrefsManager(context)
    check(prefs.loadConnections().isEmpty())
    val manager = WorkManager.getInstance(context)
    suspend fun active(expected: Boolean): Boolean {
        repeat(30) {
            val live = manager.getWorkInfosForUniqueWork("quota_periodic_refresh").get(5, TimeUnit.SECONDS)
                .any { !it.state.isFinished }
            if (live == expected) return true
            delay(100)
        }
        return false
    }
    try {
        prefs.setRefreshInterval(15)
        WorkManagerInitializer.schedulePeriodicRefresh(context)
        check(active(true))
        check(EncryptedPrefsManager(context).getRefreshInterval() == 15L)
        prefs.setRefreshInterval(0)
        WorkManagerInitializer.schedulePeriodicRefresh(context)
        check(active(false))
        check(EncryptedPrefsManager(context).getRefreshInterval() == 0L)
        // Application startup uses this same persisted-preference entry point.
        WorkManagerInitializer.schedulePeriodicRefresh(context)
        check(active(false))
        check(manager.getWorkInfosForUniqueWork("token_periodic_refresh").get(5, TimeUnit.SECONDS).none { !it.state.isFinished })
        true
    } catch (_: Exception) {
        false
    } finally {
        prefs.setRefreshInterval(30)
        WorkManagerInitializer.schedulePeriodicRefresh(context)
    }
}
