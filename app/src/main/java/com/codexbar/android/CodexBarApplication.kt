package com.codexbar.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import javax.inject.Inject

@HiltAndroidApp
class CodexBarApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        WorkManagerInitializer.schedulePeriodicRefresh(this)
    }
}
