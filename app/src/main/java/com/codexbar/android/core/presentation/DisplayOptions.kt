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
    val headlineWindow: String? = null
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
