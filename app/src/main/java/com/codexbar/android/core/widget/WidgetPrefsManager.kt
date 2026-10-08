package com.codexbar.android.core.widget

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import com.codexbar.android.core.domain.model.ReportedMoney
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.presentation.QuotaPace
import com.codexbar.android.core.presentation.WindowHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Synchronized fun deleteAccountCache(id: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cache_${id}_") || it == "history_${id}" || it == "history_${id}_generation" }.forEach(editor::remove)
        editor.apply()
    }

    @Synchronized fun recordHistory(connection: AccountConnection, quota: QuotaInfo, now: java.time.Instant = java.time.Instant.now()):
        Map<String, WindowHistory> {
        require(quota.service == connection.service)
        val key = "history_${connection.id}"
        if (prefs.getString("${key}_generation", null) != connection.generation) {
            prefs.edit().remove(key).putString("${key}_generation", connection.generation).apply()
        }
        val previous = history(connection, now)
        val next = QuotaPace.record(previous, quota, now.epochSecond)
        check(prefs.edit().putString(key, Json.encodeToString(next)).commit())
        return next
    }

    @Synchronized fun history(connection: AccountConnection, now: Instant = Instant.now()): Map<String, WindowHistory> = runCatching {
        if (prefs.getString("history_${connection.id}_generation", null) != connection.generation) emptyMap()
        else {
            val key = "history_${connection.id}"
            val encoded = prefs.getString(key, null) ?: "{}"
            require(encoded.length <= 262144)
            val history = Json.decodeFromString<Map<String, WindowHistory>>(encoded)
            QuotaPace.retained(history, now.epochSecond).also { retained ->
                if (retained != history) prefs.edit().putString(key, Json.encodeToString(retained)).apply()
            }
        }
    }.getOrDefault(emptyMap())

    fun cacheQuota(connection: AccountConnection, quota: QuotaInfo) {
        require(quota.service == connection.service)
        saveSnapshot(connection, QuotaSnapshot(quota, quota.fetchedAt))
    }

    @Synchronized fun saveSnapshot(connection: AccountConnection, snapshot: QuotaSnapshot) {
        require(snapshot.quota == null || snapshot.quota.service == connection.service)
        val prefix = "cache_${connection.id}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.putString("${prefix}generation", connection.generation)
            .putString("${prefix}snapshot_v1", Json.encodeToString(snapshot))
        check(editor.commit()) { "Could not persist quota reading" }
    }

    @Synchronized fun getSnapshot(connection: AccountConnection, now: Instant = Instant.now()): QuotaSnapshot {
        val stored = readSnapshot(connection)
        val retained = stored.retained(now)
        if (retained != stored) saveSnapshot(connection, retained)
        return retained
    }

    fun getCachedQuota(connection: AccountConnection): QuotaInfo? = getSnapshot(connection).quota

    private fun readSnapshot(connection: AccountConnection): QuotaSnapshot {
        val values = prefs.all
        val prefix = "cache_${connection.id}_"
        if (values["${prefix}generation"] != connection.generation) return QuotaSnapshot()
        val encoded = values["${prefix}snapshot_v1"] as? String
        if (encoded == null) return QuotaSnapshot(getLegacyQuota(connection)?.copy(source = "legacy"))
        return try {
            require(encoded.length <= 262144)
            val snapshot = Json.decodeFromString<QuotaSnapshot>(encoded)
            val quota = snapshot.quota
            require(quota == null || (quota.service == connection.service && quota.windows.size <= 100 &&
                quota.windows.map { it.id }.distinct().size == quota.windows.size &&
                quota.windows.all { it.utilization.isFinite() && it.utilization >= 0 }))
            snapshot
        } catch (_: Exception) { QuotaSnapshot() }
    }

    private fun getLegacyQuota(connection: AccountConnection): QuotaInfo? {
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
