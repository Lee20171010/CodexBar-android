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
    val absoluteReset: Boolean = false
) {
    fun visible(quota: QuotaInfo) = quota.windows.filter { it.id !in hiddenWindows }
    fun hiddenRisk(quota: QuotaInfo) = quota.windows.any { it.id in hiddenWindows && it.utilization >= .85 }
    fun reset(context: Context, at: Instant?, now: Instant = Instant.now()): String? =
        if (absoluteReset && at != null) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault()).format(at) else QuotaPresentation.reset(context, at, now)
}
