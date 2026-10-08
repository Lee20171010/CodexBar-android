package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.codexbar.android.R
import com.codexbar.android.core.presentation.PaceEstimate

@Composable
internal fun PaceChart(estimate: PaceEstimate, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.pace_estimate)
    Canvas(modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = description }) {
        val samples = estimate.samples
        val start = samples.first().at
        val end = (estimate.cycleEnd ?: samples.last().at).coerceAtLeast(start + 1)
        fun x(at: Long) = ((at - start).toDouble() / (end - start) * size.width).toFloat()
        fun y(remaining: Double) = ((1 - remaining.coerceIn(0.0, 1.0)) * size.height).toFloat()
        val path = Path().apply {
            samples.forEachIndexed { index, sample ->
                val point = Offset(x(sample.at), y(sample.remaining))
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(path, color, style = Stroke(3.dp.toPx()))
        if (estimate.cycleEnd != null && estimate.duration != null) {
            val remainingAtStart = (estimate.cycleEnd - start).toDouble() / estimate.duration
            drawLine(guide, Offset(0f, y(remainingAtStart)), Offset(size.width, size.height), 1.dp.toPx())
        }
    }
}
