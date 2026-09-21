package com.codexbar.android.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.PacePresentation
import com.codexbar.android.core.presentation.PaceState
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.ui.components.providerIcon
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.CodexBarStateColors
import com.codexbar.android.ui.theme.providerVisualStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

@Composable
fun ServiceCard(
    service: ServiceQuotaPresentation,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visualStyle = providerVisualStyle(service.service)
    val themeProfile = LocalCodexBarThemeProfile.current
    var previousFetchedAt by remember(service.service) {
        mutableStateOf(service.freshness.fetchedAt)
    }
    val updatePulse = remember(service.service) { Animatable(0f) }
    LaunchedEffect(service.freshness.fetchedAt) {
        val fetchedAt = service.freshness.fetchedAt
        if (previousFetchedAt != null && fetchedAt != null && previousFetchedAt != fetchedAt) {
            updatePulse.snapTo(1f)
            updatePulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing)
            )
        }
        previousFetchedAt = fetchedAt
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .graphicsLayer {
                val scale = 1f + (updatePulse.value * 0.012f)
                scaleX = scale
                scaleY = scale
            },
        shape = if (themeProfile.usesProviderCardShapes) {
            visualStyle.shape
        } else {
            MaterialTheme.shapes.large
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) {
                themeProfile.serviceCardElevation + 2.dp
            } else {
                themeProfile.serviceCardElevation
            },
            pressedElevation = themeProfile.serviceCardElevation + 4.dp
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = visualStyle.accent.copy(
                alpha = themeProfile.serviceCardBorderAlpha + (updatePulse.value * 0.5f)
            )
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (themeProfile.usesProviderCardShapes) {
                visualStyle.container
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(
                    alpha = themeProfile.serviceCardContainerAlpha
                )
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(CodexBarSpacing.large)) {
            ProviderHeader(
                service = service,
                accent = visualStyle.accent
            )

            service.insights.forEach { insight ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                ServiceInsightBanner(
                    insight = insight,
                    accent = visualStyle.accent
                )
            }

            if (service.status != ServiceQuotaStatus.Fresh &&
                service.status != ServiceQuotaStatus.Redacted
            ) {
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                ServiceError(service)
            }

            service.codexResetCredits?.let { credits ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                CodexResetCreditsCard(
                    credits = credits,
                    accent = visualStyle.accent,
                    expanded = false
                )
            }

            service.codexTelemetry?.let { telemetry ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                CodexTelemetryCompactCard(
                    telemetry = telemetry,
                    accent = visualStyle.accent
                )
            }

            service.primaryMetric?.let { metric ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.large)
                ) {
                    QuotaRingGauge(
                        metric = metric,
                        goodColor = visualStyle.accent
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
                    ) {
                        Text(
                            text = metric.usedLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = CodexBarStateColors.severityColor(
                                metric.severity,
                                visualStyle.accent
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = metric.remainingLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        PacePill(
                            pace = metric.pace,
                            goodColor = visualStyle.accent
                        )
                        metric.resetLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        listOfNotNull(
                            metric.pace.cycleProgressLabel,
                            metric.pace.usageRateLabel,
                            metric.pace.paceMultiplierLabel,
                            metric.pace.reserveLabel,
                            metric.pace.forecastLabel
                        ).distinct().takeIf { it.isNotEmpty() }?.let { details ->
                            Text(
                                text = details.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                metric.resetPlan?.let { plan ->
                    Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                    ResetPlanBanner(
                        plan = plan,
                        accent = visualStyle.accent
                    )
                }
                QuotaHistorySparkline(
                    history = metric.history,
                    accent = visualStyle.accent,
                    modifier = Modifier.padding(top = CodexBarSpacing.small)
                )
            }

            val secondaryMetrics = service.metrics.filterNot {
                it.id == service.primaryMetric?.id
            }
            if (secondaryMetrics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(CodexBarSpacing.large))
                Column(verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)) {
                    secondaryMetrics.forEach { metric ->
                        QuotaGaugeBar(
                            metric = metric,
                            showExtendedDetails = true,
                            goodColor = visualStyle.accent
                        )
                        metric.resetPlan?.let { plan ->
                            ResetPlanBanner(
                                plan = plan,
                                accent = visualStyle.accent
                            )
                        }
                    }
                }
            }

            service.extraUsage?.let { extra ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                Text(
                    text = stringResource(
                        R.string.extra_usage_summary,
                        extra.label,
                        extra.usedCreditsLabel,
                        extra.limitLabel,
                        extra.remainingLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            service.balance?.let { balance ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                Text(
                    text = "${balance.label}: ${balance.amountLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            service.renewal?.let { renewal ->
                Spacer(modifier = Modifier.height(CodexBarSpacing.medium))
                Text(
                    text = stringResource(R.string.renewal_summary, renewal.valueLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(CodexBarSpacing.small))
            Text(
                text = service.freshness.ageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderHeader(
    service: ServiceQuotaPresentation,
    accent: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = MaterialTheme.shapes.small,
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = service.service.providerIcon(),
                    contentDescription = service.service.displayName,
                    modifier = Modifier.size(24.dp),
                    tint = accent
                )
            }
        }
        Spacer(modifier = Modifier.width(CodexBarSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = service.service.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                service.tier?.let { tier ->
                    Spacer(modifier = Modifier.width(CodexBarSpacing.small))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = CodexBarSpacing.small,
                                vertical = CodexBarSpacing.xsmall
                            ),
                            color = accent
                        )
                    }
                }
            }
            service.accountLabel?.takeIf { it.isNotBlank() }?.let { account ->
                Text(
                    text = account,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        StatusPill(status = service.status, freshColor = accent)
    }
}

@Composable
private fun ServiceError(service: ServiceQuotaPresentation) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Error,
            contentDescription = stringResource(R.string.content_description_error),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(CodexBarSpacing.small))
        Text(
            text = service.freshness.staleReason ?: service.status.toLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PacePill(
    pace: PacePresentation,
    goodColor: Color
) {
    val color = when (pace.state) {
        PaceState.OnTrack -> goodColor
        PaceState.AtRisk -> CodexBarStateColors.warningColor()
        PaceState.Exhausting -> MaterialTheme.colorScheme.error
        PaceState.Unknown,
        PaceState.CollectingHistory -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CodexBarSpacing.small,
                vertical = CodexBarSpacing.xsmall
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = pace.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServiceQuotaStatus.toLabel(): String {
    return when (this) {
        ServiceQuotaStatus.Fresh -> stringResource(R.string.status_fresh)
        ServiceQuotaStatus.Stale -> stringResource(R.string.status_stale)
        ServiceQuotaStatus.Loading -> stringResource(R.string.status_loading)
        ServiceQuotaStatus.AuthRequired -> stringResource(R.string.status_reauthentication_required)
        ServiceQuotaStatus.RateLimited -> stringResource(R.string.status_rate_limited)
        ServiceQuotaStatus.Offline -> stringResource(R.string.status_offline)
        ServiceQuotaStatus.ProviderError -> stringResource(R.string.status_provider_error)
        ServiceQuotaStatus.Disconnected -> stringResource(R.string.status_not_connected)
        ServiceQuotaStatus.Redacted -> stringResource(R.string.status_quota_hidden)
    }
}

@Composable
private fun StatusPill(status: ServiceQuotaStatus, freshColor: Color) {
    val color = status.color(freshColor)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = status.toStatusLabel(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(
                horizontal = CodexBarSpacing.small,
                vertical = CodexBarSpacing.xsmall
            )
        )
    }
}

@Composable
private fun ServiceQuotaStatus.color(freshColor: Color): Color {
    return when (this) {
        ServiceQuotaStatus.Fresh -> freshColor
        ServiceQuotaStatus.Redacted -> MaterialTheme.colorScheme.outline
        ServiceQuotaStatus.Stale,
        ServiceQuotaStatus.Loading,
        ServiceQuotaStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        ServiceQuotaStatus.AuthRequired,
        ServiceQuotaStatus.RateLimited,
        ServiceQuotaStatus.Offline,
        ServiceQuotaStatus.ProviderError -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun ServiceQuotaStatus.toStatusLabel(): String {
    return when (this) {
        ServiceQuotaStatus.Fresh -> stringResource(R.string.status_up_to_date)
        ServiceQuotaStatus.Redacted -> stringResource(R.string.status_hidden)
        else -> toLabel()
    }
}
