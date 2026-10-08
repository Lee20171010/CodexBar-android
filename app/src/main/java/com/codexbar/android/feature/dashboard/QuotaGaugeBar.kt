package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaPresentation
import java.time.Instant

@Composable
fun QuotaGaugeBar(
    utilization: Double,
    modifier: Modifier = Modifier,
    label: String? = null,
    resetsAt: Instant? = null,
    now: Instant = Instant.now(),
    display: com.codexbar.android.core.presentation.DisplayOptions = com.codexbar.android.core.presentation.DisplayOptions()
) {
    val remaining = (1 - utilization).coerceIn(0.0, 1.0).toFloat()
    val color = if (utilization >= .85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.percent_left, QuotaPresentation.remainingPercent(utilization)),
                style = MaterialTheme.typography.labelLarge, color = color)
        }
        LinearProgressIndicator(progress = { remaining }, modifier = Modifier.fillMaxWidth().height(4.dp), color = color)
        display.reset(LocalContext.current, resetsAt, now)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
