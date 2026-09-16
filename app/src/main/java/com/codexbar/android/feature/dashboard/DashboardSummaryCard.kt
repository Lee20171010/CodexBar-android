package com.codexbar.android.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.ui.components.providerIcon
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.CodexBarStateColors
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.providerVisualStyle

/**
 * Opens the dashboard with the one number that matters, then a scannable strip of every
 * connected provider, so the answer to "am I about to run out" does not require scrolling.
 */
@Composable
fun DashboardSummaryCard(
    summary: DashboardSummary,
    onProviderClick: (AiService) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeProfile = LocalCodexBarThemeProfile.current
    val tightest = summary.tightest
    val accent = tightest?.let { providerVisualStyle(it.service).accent }
    val severityColor = CodexBarStateColors.severityColor(
        tightest?.severity ?: QuotaSeverity.Unknown,
        accent
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = themeProfile.serviceCardElevation
        ),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                alpha = themeProfile.serviceCardContainerAlpha
            )
        )
    ) {
        Column(modifier = Modifier.padding(CodexBarSpacing.large)) {
            Text(
                text = stringResource(R.string.dashboard_summary_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(CodexBarSpacing.small))

            if (tightest == null) {
                Text(
                    text = stringResource(R.string.dashboard_summary_no_reading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = tightest.remainingLabel,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = severityColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(CodexBarSpacing.xsmall))
                Text(
                    text = stringResource(
                        R.string.dashboard_summary_window,
                        tightest.service.displayName,
                        tightest.metricLabel
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                listOfNotNull(tightest.resetLabel, tightest.paceLabel)
                    .takeIf { it.isNotEmpty() }
                    ?.let { details ->
                        Text(
                            text = details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                SeverityTrack(usedFraction = tightest.usedFraction, color = severityColor)
            }

            if (summary.pulses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(CodexBarSpacing.large))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
                ) {
                    summary.pulses.forEach { pulse ->
                        ProviderPulseChip(
                            pulse = pulse,
                            onClick = { onProviderClick(pulse.service) }
                        )
                    }
                }
            }

            if (summary.attentionCount > 0) {
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(CodexBarSpacing.small))
                    Text(
                        text = stringResource(
                            R.string.dashboard_summary_attention,
                            summary.attentionCount,
                            summary.connectedCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityTrack(usedFraction: Double, color: androidx.compose.ui.graphics.Color) {
    val animatedFraction by animateFloatAsState(
        targetValue = usedFraction.toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 520),
        label = "summaryTrack"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .height(10.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ProviderPulseChip(pulse: ProviderPulse, onClick: () -> Unit) {
    val accent = providerVisualStyle(pulse.service).accent
    val color = if (pulse.needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        CodexBarStateColors.severityColor(pulse.severity, accent)
    }
    val valueLabel = pulse.remainingPercent
        ?.let { stringResource(R.string.dashboard_summary_pulse_value, it) }
        ?: stringResource(R.string.dashboard_summary_pulse_unknown)

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
        modifier = Modifier.semanticsLabel(
            stringResource(
                R.string.dashboard_summary_pulse_description,
                pulse.service.displayName,
                valueLabel
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CodexBarSpacing.medium,
                vertical = CodexBarSpacing.small
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            Icon(
                imageVector = pulse.service.providerIcon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier {
    return clearAndSetSemantics { contentDescription = label }
}
