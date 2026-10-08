package com.codexbar.android.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.balanceText
import com.codexbar.android.core.presentation.QuotaPresentation
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

open class QuotaGlanceWidget(private val singleAccount: Boolean = false) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 120.dp), DpSize(250.dp, 240.dp), DpSize(350.dp, 360.dp)))

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun accounts(): EncryptedPrefsManager
        fun widgets(): WidgetPrefsManager
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val pins = dependencies.widgets().getSelectedConnections(widgetId)
        val accounts = dependencies.accounts().loadConnections().associateBy { it.id }
        val selected = pins.sorted().map { accounts[it] }.let { if (singleAccount) it.take(1) else it }
        provideContent {
            GlanceTheme {
                val height = LocalSize.current.height
                val capacity = if (singleAccount) 1 else if (height >= 360.dp) 3 else if (height >= 240.dp) 2 else 1
                Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1C1B1F))).cornerRadius(20.dp)
                    .clickable(actionStartActivity<MainActivity>()).padding(12.dp)) {
                    if (selected.isEmpty()) Label(context.getString(R.string.widget_no_accounts))
                    selected.take(capacity).forEachIndexed { index, account ->
                        if (account == null) Label(context.getString(R.string.widget_account_unavailable))
                        else AccountSection(account, dependencies.widgets(), widgetId, index == 0, singleAccount && height >= 240.dp)
                        if (index < capacity - 1) Spacer(GlanceModifier.height(8.dp))
                    }
                    if (selected.size > capacity) Label(context.getString(R.string.widget_more_accounts, selected.size - capacity))
                }
            }
        }
    }

    @Composable
    private fun AccountSection(account: AccountConnection, prefs: WidgetPrefsManager, widgetId: Int, refresh: Boolean, expanded: Boolean) {
        val context = LocalContext.current
        val snapshot = prefs.getSnapshot(account)
        val quota = snapshot.quota
        val display = prefs.displayOptions(account, widgetId)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(account.name, GlanceModifier.defaultWeight(), maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            if (refresh) Image(ImageProvider(R.drawable.ic_refresh), context.getString(R.string.widget_refresh),
                GlanceModifier.size(48.dp).clickable(actionRunCallback<RefreshWidgetAction>()).padding(12.dp),
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)))
        }
        Label(QuotaPresentation.status(context, snapshot))
        if (quota != null && display.hiddenRisk(quota)) Label(context.getString(R.string.hidden_quota_risk))
        val principal = quota?.let(display::visible).orEmpty().filterNot { it.supplemental }
        val windows = if (expanded) principal.take(if (LocalSize.current.height >= 360.dp) 4 else 2)
            else principal.sortedByDescending { it.utilization }.take(1)
        windows.forEach { window ->
            Label("${window.label} · ${context.getString(R.string.percent_left, QuotaPresentation.remainingPercent(window.utilization))}")
            if (expanded) {
                LinearProgressIndicator((1 - window.utilization).toFloat().coerceIn(0f, 1f), GlanceModifier.fillMaxWidth())
                display.reset(context, window.resetsAt)?.let { Label(it) }
                Spacer(GlanceModifier.height(8.dp))
            }
        }
        if (expanded && principal.size > windows.size) Label(context.getString(R.string.widget_more_windows, principal.size - windows.size))
        quota?.money?.takeIf { display.showAmounts }?.let { Label(it.balanceText()) }
        if (quota == null) Label(context.getString(R.string.widget_open_app))
    }

    @Composable
    private fun Label(text: String) {
        Text(text, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
    }
}

class SingleAccountQuotaWidget : QuotaGlanceWidget(singleAccount = true)

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val dependencies = EntryPointAccessors.fromApplication(context, QuotaGlanceWidget.Dependencies::class.java)
        val selected = dependencies.widgets().getSelectedConnections(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
        WorkManagerInitializer.enqueueRefresh(context, manual = true,
            connections = dependencies.accounts().loadConnections().filter { it.id in selected })
    }
}
