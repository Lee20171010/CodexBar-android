package com.codexbar.android.core.presentation

import android.content.Context
import com.codexbar.android.core.domain.model.QuotaInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data class DisplayOptions(
    val hiddenWindows: Set<String> = emptySet(),
    val showAmounts: Boolean = true,
    val absoluteReset: Boolean = false,
    val fillUsed: Boolean = false,
    val showMarkers: Boolean = true,
    val accent: String? = null,
    val headlineWindow: String? = null,
    val workDays: Int? = null,
    // Global-only control (Mac single `paceVisible` pref): account/widget overrides always inherit.
    val showPace: Boolean = true,
    // Mac mergedOverviewLayout (default .detailed, SettingsStore+Defaults.swift:835-837): compact
    // metric rows keep title/percent/bar and drop reset/meta/detail annotations (MenuCardMetricRow.swift:28-48).
    // Global-only like showPace: account/widget overrides always inherit.
    val compactOverview: Boolean = false
) {
    fun visible(quota: QuotaInfo) = quota.windows.filter { it.id !in hiddenWindows }
    fun hiddenRisk(quota: QuotaInfo) = quota.windows.any { it.id in hiddenWindows && it.utilization >= .85 }
    fun reset(context: Context, at: Instant?, now: Instant = Instant.now()): String? =
        if (absoluteReset && at != null) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault()).format(at) else QuotaPresentation.reset(context, at, now)

    /**
     * Mac warning markers: default remaining thresholds [50, 20]%
     * (QuotaWarningThresholds.defaults); positions measured from the bar's leading edge,
     * mirrored when the bar fills with used instead of remaining.
     */
    fun markerFractions(): List<Float> =
        if (!showMarkers) emptyList() else listOf(.5f, .2f).map { if (fillUsed) 1f - it else it }
}

/**
 * Stored display record. `v = 0` is a legacy full profile: missing fields mean DisplayOptions
 * defaults, never inheritance. `v = 1` records per-field inheritance: fields named in `explicit`
 * win, all others follow the parent (global for connections, the connection chain for widgets).
 */
@Serializable
data class DisplayOverride(
    val v: Int = 0,
    val explicit: Set<String> = emptySet(),
    val hiddenWindows: Set<String>? = null,
    val showAmounts: Boolean? = null,
    val absoluteReset: Boolean? = null,
    val fillUsed: Boolean? = null,
    val showMarkers: Boolean? = null,
    val accent: String? = null,
    val headlineWindow: String? = null,
    val workDays: Int? = null
) {
    fun resolve(parent: DisplayOptions): DisplayOptions = if (v != 1) DisplayOptions(
        hiddenWindows ?: emptySet(), showAmounts ?: true, absoluteReset ?: false, fillUsed ?: false,
        showMarkers ?: true, accent, headlineWindow, workDays,
        // A legacy full profile predates showPace and must not pin a control no override UI can set.
        showPace = parent.showPace,
        compactOverview = parent.compactOverview
    ) else DisplayOptions(
        hiddenWindows = if ("hiddenWindows" in explicit) hiddenWindows ?: emptySet() else parent.hiddenWindows,
        showAmounts = if ("showAmounts" in explicit) showAmounts ?: true else parent.showAmounts,
        absoluteReset = if ("absoluteReset" in explicit) absoluteReset ?: false else parent.absoluteReset,
        fillUsed = if ("fillUsed" in explicit) fillUsed ?: false else parent.fillUsed,
        showMarkers = if ("showMarkers" in explicit) showMarkers ?: true else parent.showMarkers,
        accent = if ("accent" in explicit) accent else parent.accent,
        headlineWindow = if ("headlineWindow" in explicit) headlineWindow else parent.headlineWindow,
        workDays = if ("workDays" in explicit) workDays else parent.workDays,
        // Global-only control: never pinnable by an account or widget record, both chains inherit.
        showPace = parent.showPace,
        compactOverview = parent.compactOverview
    )

    /** Materializes the stored values without inheritance; matches the pre-inheritance raw decode. */
    fun stored(): DisplayOptions = DisplayOptions(
        hiddenWindows ?: emptySet(), showAmounts ?: true, absoluteReset ?: false, fillUsed ?: false,
        showMarkers ?: true, accent, headlineWindow, workDays
    )

    companion object {
        val FIELDS = setOf("hiddenWindows", "showAmounts", "absoluteReset", "fillUsed", "showMarkers",
            "accent", "headlineWindow", "workDays")

        /** New stored record: fields named in `explicit` pin the values from options, the rest inherit. */
        fun pinning(options: DisplayOptions, explicit: Set<String>) = DisplayOverride(
            v = 1, explicit = explicit,
            hiddenWindows = options.hiddenWindows, showAmounts = options.showAmounts,
            absoluteReset = options.absoluteReset, fillUsed = options.fillUsed,
            showMarkers = options.showMarkers, accent = options.accent,
            headlineWindow = options.headlineWindow, workDays = options.workDays
        )
    }
}
