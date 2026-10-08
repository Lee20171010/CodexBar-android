package com.codexbar.android.core.widget

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.ReportedMoney
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Widget pins and display-only cache. Credentials remain in encrypted account storage. */
@Singleton
class WidgetPrefsManager internal constructor(private val prefs: SharedPreferences) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences("codexbar_widget_prefs", Context.MODE_PRIVATE)
    )

    fun saveSelectedConnections(appWidgetId: Int, ids: Set<String>) {
        check(prefs.edit().putStringSet("widget_${appWidgetId}_connections", ids).commit())
    }

    fun getSelectedConnections(appWidgetId: Int): Set<String> {
        prefs.getStringSet("widget_${appWidgetId}_connections", null)?.let { return it.toSet() }
        // Legacy provider pins refer only to the adopted legacy IDs, never a new sibling.
        return prefs.getStringSet("widget_${appWidgetId}_services", null).orEmpty()
            .filter { name -> AiService.entries.any { it.name == name } }.toSet()
    }

    fun deleteWidgetConfig(appWidgetId: Int) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("widget_${appWidgetId}_") }.forEach(editor::remove)
        editor.apply()
    }

    fun deleteAccountCache(id: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cache_${id}_") }.forEach(editor::remove)
        editor.apply()
    }

    fun cacheQuota(connection: AccountConnection, quota: QuotaInfo) {
        require(quota.service == connection.service)
        val prefix = "cache_${connection.id}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.putString("${prefix}generation", connection.generation)
            .putStringSet("${prefix}labels", quota.windows.map { it.label }.toSet())
            .putString("${prefix}tier", quota.tier)
            .putLong("${prefix}updated_at", quota.fetchedAt.toEpochMilli())
        quota.windows.forEach {
            editor.putString("${prefix}${it.label}_id", it.id)
            editor.putString("${prefix}${it.label}_kind", it.kind.name)
            editor.putFloat("${prefix}${it.label}_util", it.utilization.toFloat())
            it.resetsAt?.let { reset -> editor.putLong("${prefix}${it.label}_resets", reset.epochSecond) }
        }
        quota.money?.let {
            editor.putString("${prefix}money_currency", it.currency)
                .putString("${prefix}money_period", it.period)
                .putString("${prefix}money_balance", it.balance?.toString())
                .putString("${prefix}money_spent", it.spent?.toString())
                .putLong("${prefix}money_at", it.fetchedAt.toEpochMilli())
        }
        editor.apply()
    }

    fun getCachedQuota(connection: AccountConnection): QuotaInfo? {
        // One immutable preference snapshot: reconnect cannot mix generations across window reads.
        val snapshot = prefs.all
        val prefix = "cache_${connection.id}_"
        if (snapshot["${prefix}generation"] != connection.generation) return null
        val updated = snapshot["${prefix}updated_at"] as? Long ?: return null
        val labels = snapshot["${prefix}labels"] as? Set<*> ?: return null
        val windows = labels.filterIsInstance<String>().sorted().map { label ->
            val utilization = snapshot["${prefix}${label}_util"] as? Float ?: return null
            if (!utilization.isFinite() || utilization < 0f) return null
            UsageWindow(label, utilization.toDouble(),
                (snapshot["${prefix}${label}_resets"] as? Long)?.let(Instant::ofEpochSecond),
                id = snapshot["${prefix}${label}_id"] as? String ?: label,
                kind = UsageWindowKind.entries.firstOrNull { it.name == snapshot["${prefix}${label}_kind"] } ?: UsageWindowKind.QUOTA)
        }
        val money = (snapshot["${prefix}money_currency"] as? String)?.let { currency ->
            fun amount(key: String) = (snapshot["${prefix}money_$key"] as? String)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0 }
            ReportedMoney(amount("balance"), amount("spent"), currency,
                snapshot["${prefix}money_period"] as? String,
                Instant.ofEpochMilli(snapshot["${prefix}money_at"] as? Long ?: return null))
        }
        return QuotaInfo(connection.service, windows, null, snapshot["${prefix}tier"] as? String, Instant.ofEpochMilli(updated), money)
    }
}
