package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaPresentation
import java.time.Instant

/**
 * Mac menu-card MetricRow internalized for touch:
 * title+percent as one monochrome line, reset aligned right on the same line,
 * a 6dp capsule bar tinted with the provider accent, meta below in 12sp secondary.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotaGaugeBar(
    utilization: Double,
    modifier: Modifier = Modifier,
    label: String? = null,
    resetsAt: Instant? = null,
    now: Instant = Instant.now(),
    display: com.codexbar.android.core.presentation.DisplayOptions = com.codexbar.android.core.presentation.DisplayOptions(),
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val remaining = (1 - utilization).coerceIn(0.0, 1.0)
    val resetText = display.reset(LocalContext.current, resetsAt, now)
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Native MetricRowHeader uses a trailing reset line when the header cannot fit.
        FlowRow(horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${label.orEmpty()} ${stringResource(R.string.percent_left, QuotaPresentation.remainingPercent(utilization))}".trim(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            resetText?.let {
                Text(
                    it,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.End
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val fill = if (display.fillUsed) (utilization).toFloat().coerceIn(0f, 1f) else remaining.toFloat()
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(accent))
            val markers = display.markerFractions()
            val gapColor = MaterialTheme.colorScheme.surfaceContainerHighest
            if (markers.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                // Mac punches the warning thresholds out of the bar; a gap-colored line is the touch equivalent.
                markers.forEach { fraction ->
                    val x = size.width * fraction
                    drawLine(gapColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
