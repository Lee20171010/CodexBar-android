package com.codexbar.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codexbar.android.core.workmanager.WorkManagerInitializer

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == QuotaNotificationService.ACTION_REFRESH) {
            WorkManagerInitializer.enqueueRefresh(context, manual = true)
        }
    }
}
