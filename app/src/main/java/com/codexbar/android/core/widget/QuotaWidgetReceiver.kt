package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log
import com.codexbar.android.core.tile.QuotaTileService
import kotlinx.coroutines.CancellationException

/** Call from the initiating UI/worker scope after account or cache changes. */
suspend fun updateQuotaSurfaces(context: Context) {
    try {
        QuotaGlanceWidget().updateAll(context)
        TileService.requestListeningState(context, ComponentName(context, QuotaTileService::class.java))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Log.w("QuotaWidgets", "Launcher surfaces could not be refreshed; cached data is retained.")
    }
}

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = QuotaGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val widgetPrefs = WidgetPrefsManager(context)
        for (id in appWidgetIds) {
            widgetPrefs.deleteWidgetConfig(id)
        }
    }
}
