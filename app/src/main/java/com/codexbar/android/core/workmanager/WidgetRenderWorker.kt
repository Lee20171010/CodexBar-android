package com.codexbar.android.core.workmanager

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.widget.QuotaGlanceWidget
import kotlinx.coroutines.CancellationException

/**
 * Re-renders a widget after the launcher has finished binding it.
 *
 * Some launchers can ignore the update sent while a configuration activity is still open.
 * This worker performs no provider I/O and is safe to run without a network connection.
 */
class WidgetRenderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, INVALID_APP_WIDGET_ID)
        if (appWidgetId == INVALID_APP_WIDGET_ID) return Result.failure()

        return try {
            val glanceId = GlanceAppWidgetManager(applicationContext)
                .getGlanceIdBy(appWidgetId)
            QuotaGlanceWidget().update(applicationContext, glanceId)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Widget render failed for id=$appWidgetId", error)
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                publishUnrenderableState(appWidgetId)
                Result.failure()
            }
        }
    }

    /**
     * Replaces the provider's initial loading layout after Glance has exhausted its retries.
     *
     * Without this the launcher keeps an indefinite "Loading…" tile that cannot be tapped, so the
     * only remaining recovery is removing and re-adding the widget.
     */
    private fun publishUnrenderableState(appWidgetId: Int) {
        runCatching {
            val openApp = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("codexbar://dashboard")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val views = RemoteViews(
                applicationContext.packageName,
                R.layout.widget_error
            ).apply {
                setOnClickPendingIntent(
                    R.id.widget_error_root,
                    PendingIntent.getActivity(
                        applicationContext,
                        appWidgetId,
                        openApp,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            AppWidgetManager.getInstance(applicationContext)
                .updateAppWidget(appWidgetId, views)
        }.onFailure { error ->
            Log.e(TAG, "Could not publish the fallback layout for id=$appWidgetId", error)
        }
    }

    companion object {
        const val KEY_APP_WIDGET_ID = "app_widget_id"
        const val INVALID_APP_WIDGET_ID = -1
        private const val MAX_RETRY_COUNT = 2
        private const val TAG = "CodexBarWidget"
    }
}
