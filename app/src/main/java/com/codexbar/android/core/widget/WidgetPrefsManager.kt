package com.codexbar.android.core.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class WidgetDisplayConfig(
    val services: List<AiService> = emptyList(),
    val showReset: Boolean = true,
    val showPace: Boolean = true,
    val showFreshness: Boolean = true,
    val maxRows: Int = 4
)

/**
 * Manages per-widget configuration (selected services) and cached quota data.
 * Uses plain SharedPreferences (not encrypted) since widget data is non-sensitive display info.
 */
@Singleton
class WidgetPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Per-widget service selection ---

    fun saveWidgetConfig(appWidgetId: Int, config: WidgetDisplayConfig) {
        val prefix = "widget_${appWidgetId}"
        prefs.edit()
            .putString("${prefix}_services_order", config.services.joinToString(",") { it.name })
            .putStringSet("${prefix}_services", config.services.map { it.name }.toSet())
            .putBoolean("${prefix}_show_reset", config.showReset)
            .putBoolean("${prefix}_show_pace", config.showPace)
            .putBoolean("${prefix}_show_freshness", config.showFreshness)
            .putInt("${prefix}_max_rows", config.maxRows.coerceIn(1, 12))
            .commit()
    }

    fun getWidgetConfig(appWidgetId: Int): WidgetDisplayConfig {
        val prefix = "widget_${appWidgetId}"
        val orderedServices = prefs.getString("${prefix}_services_order", null)
            ?.split(",")
            ?.mapNotNull { name -> name.toAiServiceOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: prefs.getStringSet("${prefix}_services", null)
                ?.mapNotNull { name -> name.toAiServiceOrNull() }
                ?.sortedBy { it.ordinal }
            ?: emptyList()

        return WidgetDisplayConfig(
            services = orderedServices,
            showReset = prefs.getBoolean("${prefix}_show_reset", true),
            showPace = prefs.getBoolean("${prefix}_show_pace", true),
            showFreshness = prefs.getBoolean("${prefix}_show_freshness", true),
            maxRows = prefs.getInt("${prefix}_max_rows", 4).coerceIn(1, 12)
        )
    }

    fun deleteWidgetConfig(appWidgetId: Int) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("widget_${appWidgetId}_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun deleteServiceCache(service: AiService) {
        val prefix = "cache_${service.name}"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun deleteAllServiceCaches() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cache_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    // --- Cached quota data for widgets ---

    fun cachePresentation(service: ServiceQuotaPresentation) {
        val prefix = "cache_${service.service.name}"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }

        val labels = service.metrics.map { it.label }
        editor.putString("${prefix}_labels", labels.joinToString(","))
        editor.putLong("${prefix}_updated_at", System.currentTimeMillis())
        editor.putString("${prefix}_status", service.status.name)
        editor.putString("${prefix}_freshness", service.freshness.ageLabel)
        service.freshness.staleReason?.let {
            editor.putString("${prefix}_status_message", it)
        }

        service.tier?.let { editor.putString("${prefix}_tier", it) }
        if (service.metrics.isEmpty()) {
            val message = service.tier
                ?: ContextCompat.getContextForLanguage(context)
                    .getString(R.string.widget_no_bounded_quota)
            editor.putString("${prefix}_status_message", message)
        }

        for (metric in service.metrics) {
            editor.cacheMetric(prefix, metric)
        }
        editor.apply()
    }

    private fun SharedPreferences.Editor.cacheMetric(prefix: String, metric: QuotaMetricPresentation) {
        val label = metric.label
        putFloat("${prefix}_${label}_util", (metric.usedFraction ?: 0.0).toFloat())
        putFloat("${prefix}_${label}_bar", metric.barProgress)
        putString("${prefix}_${label}_remaining_label", metric.remainingLabel)
        putString("${prefix}_${label}_used_label", metric.usedLabel)
        putString("${prefix}_${label}_reset_label", metric.resetLabel)
        putString("${prefix}_${label}_pace_label", metric.pace.label)
        putString("${prefix}_${label}_reset_plan_label", metric.resetPlan?.compactActionLabel)
        putString("${prefix}_${label}_severity", metric.severity.name)
        metric.resetsAt?.let { putLong("${prefix}_${label}_resets", it.epochSecond) }
    }

    fun getCachedLabels(service: AiService): List<String> {
        val raw = prefs.getString("cache_${service.name}_labels", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    fun getCachedUtilization(service: AiService, label: String): Float {
        return prefs.getFloat("cache_${service.name}_${label}_util", 0f)
    }

    fun getCachedBarProgress(service: AiService, label: String): Float {
        return prefs.getFloat("cache_${service.name}_${label}_bar", (1f - getCachedUtilization(service, label)).coerceIn(0f, 1f))
    }

    fun getCachedRemainingLabel(service: AiService, label: String): String {
        return prefs.getString("cache_${service.name}_${label}_remaining_label", null)
            ?: ContextCompat.getContextForLanguage(context).getString(
                R.string.quota_remaining_percent,
                ((1f - getCachedUtilization(service, label)) * 100).toInt().coerceIn(0, 100)
            )
    }

    fun getCachedResetLabel(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_reset_label", null)
    }

    fun getCachedPaceLabel(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_pace_label", null)
    }

    fun getCachedResetPlanLabel(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_reset_plan_label", null)
    }

    fun getCachedSeverity(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_severity", null)
    }

    fun getCachedFreshness(service: AiService): String? {
        return prefs.getString("cache_${service.name}_freshness", null)
    }

    fun getCachedStatusMessage(service: AiService): String? {
        return prefs.getString("cache_${service.name}_status_message", null)
    }

    fun cacheStatusMessageIfEmpty(service: AiService, message: String) {
        if (getCachedLabels(service).isNotEmpty()) return
        val prefix = "cache_${service.name}"
        prefs.edit()
            .putString("${prefix}_status_message", message)
            .putLong("${prefix}_updated_at", System.currentTimeMillis())
            .apply()
    }

    fun getCachedUpdatedAt(service: AiService): Long {
        return prefs.getLong("cache_${service.name}_updated_at", 0L)
    }

    fun getCachedTier(service: AiService): String? {
        return prefs.getString("cache_${service.name}_tier", null)
    }

    private fun String.toAiServiceOrNull(): AiService? {
        return try {
            AiService.valueOf(this)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PREFS_NAME = "codexbar_widget_prefs"
        const val BACKUP_PATH = "$PREFS_NAME.xml"
    }
}
