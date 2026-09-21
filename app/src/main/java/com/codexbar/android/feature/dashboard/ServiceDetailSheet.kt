package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.codexbar.android.R
import com.codexbar.android.core.presentation.ExtraUsagePresentation
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.ui.components.providerIcon
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.CodexBarStateColors
import com.codexbar.android.ui.theme.providerVisualStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailSheet(
    service: ServiceQuotaPresentation,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onManageConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        ServiceDetailContent(
            service = service,
            onRefresh = onRefresh,
            onManageConnection = onManageConnection,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodexBarSpacing.large)
                .padding(bottom = CodexBarSpacing.xlarge)
        )
    }
}

@Composable
fun ServiceDetailPane(
    service: ServiceQuotaPresentation,
    onRefresh: () -> Unit,
    onManageConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visualStyle = providerVisualStyle(service.service)
    val themeProfile = LocalCodexBarThemeProfile.current
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = if (themeProfile.usesProviderCardShapes) {
            visualStyle.shape
        } else {
            MaterialTheme.shapes.large
        },
        color = if (themeProfile.usesProviderCardShapes) {
            visualStyle.container
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(
                alpha = themeProfile.serviceCardContainerAlpha
            )
        },
        border = BorderStroke(1.dp, visualStyle.accent.copy(alpha = 0.3f))
    ) {
        ServiceDetailContent(
            service = service,
            onRefresh = onRefresh,
            onManageConnection = onManageConnection,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CodexBarSpacing.xlarge)
        )
    }
}

@Composable
private fun ServiceDetailContent(
    service: ServiceQuotaPresentation,
    onRefresh: () -> Unit,
    onManageConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = providerVisualStyle(service.service).accent
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.large)
    ) {
        ServiceDetailHeader(service, accent)
        ServiceStateSummary(service, accent)
        service.codexResetCredits?.let { credits ->
            CodexResetCreditsCard(
                credits = credits,
                accent = accent,
                expanded = true
            )
        }
        service.codexTelemetry?.let { telemetry ->
            SectionTitle(stringResource(R.string.codex_telemetry_section_title))
            CodexTelemetryDetail(telemetry = telemetry, accent = accent)
        }
        service.insights.forEach { insight ->
            ServiceInsightBanner(insight = insight, accent = accent)
        }

        if (service.metrics.isNotEmpty()) {
            SectionTitle(stringResource(R.string.quota_windows))
            Column(verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)) {
                service.metrics.forEach { metric ->
                    MetricDetailCard(metric, accent)
                }
            }
        }

        service.extraUsage?.let { extraUsage ->
            SectionTitle(stringResource(R.string.extra_usage))
            ExtraUsageCard(extraUsage, accent)
        }

        service.balance?.let { balance ->
            DetailRow(balance.label, balance.amountLabel)
        }

        service.renewal?.let { renewal ->
            DetailRow(stringResource(R.string.detail_renews), renewal.valueLabel)
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.action_refresh))
            }
            Button(
                onClick = onManageConnection,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.action_manage_connection))
            }
        }
    }
}

@Composable
private fun ServiceDetailHeader(service: ServiceQuotaPresentation, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
    ) {
        Icon(
            imageVector = service.service.providerIcon(),
            contentDescription = service.service.displayName,
            modifier = Modifier.size(40.dp),
            tint = accent
        )
        Column(modifier = Modifier.weight(1f)) {
            val subtitle = listOfNotNull(service.tier, service.accountLabel)
                .joinToString(" - ")
            Text(
                text = service.service.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (subtitle.isBlank()) {
                    stringResource(R.string.quota_monitor)
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val statusColor = service.status.statusColor(accent)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = service.status.toDetailLabel(),
                modifier = Modifier.padding(horizontal = CodexBarSpacing.small, vertical = CodexBarSpacing.xsmall),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ServiceStateSummary(service: ServiceQuotaPresentation, accent: Color) {
    val statusColor = service.status.statusColor(accent)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            DetailRow(stringResource(R.string.detail_status), service.status.toDetailLabel(), statusColor)
            DetailRow(stringResource(R.string.detail_freshness), service.freshness.ageLabel)
            service.freshness.staleReason?.let {
                DetailRow(stringResource(R.string.detail_reason), it, statusColor)
            }
            service.freshness.nextRetryAt?.let {
                DetailRow(stringResource(R.string.detail_next_retry), formatDetailInstant(it))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricDetailCard(metric: QuotaMetricPresentation, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = metric.remainingLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = CodexBarStateColors.severityColor(metric.severity, accent),
                    fontWeight = FontWeight.SemiBold
                )
            }

            QuotaGaugeBar(metric = metric, goodColor = accent)

            QuotaHistoryChart(
                history = metric.history,
                accent = accent
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small),
                verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
            ) {
                DetailChip(metric.usedLabel)
                DetailChip(metric.remainingLabel)
                metric.resetLabel?.let { DetailChip(it) }
                metric.pace.cycleProgressLabel?.let { DetailChip(it) }
                metric.pace.usageRateLabel?.let { DetailChip(it) }
                metric.pace.paceMultiplierLabel?.let { DetailChip(it) }
                metric.pace.reserveLabel?.let { DetailChip(it) }
            }

            metric.resetPlan?.let { plan ->
                ResetPlanBanner(
                    plan = plan,
                    accent = accent
                )
            }

            if (metric.pace.label.isNotBlank()) {
                DetailRow(stringResource(R.string.detail_pace), metric.pace.label)
            }
            metric.pace.forecastLabel?.let {
                DetailRow(stringResource(R.string.detail_forecast), it)
            }
            metric.resetsAt?.let {
                DetailRow(stringResource(R.string.detail_reset_time), formatDetailInstant(it))
            }
        }
    }
}

@Composable
private fun ExtraUsageCard(extraUsage: ExtraUsagePresentation, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            DetailRow(extraUsage.label, extraUsage.usedCreditsLabel)
            DetailRow(stringResource(R.string.detail_limit), extraUsage.limitLabel)
            DetailRow(
                stringResource(R.string.detail_remaining),
                extraUsage.remainingLabel,
                CodexBarStateColors.severityColor(extraUsage.severity, accent)
            )
        }
    }
}

@Composable
private fun formatDetailInstant(instant: Instant): String {
    val languageContext = ContextCompat.getContextForLanguage(LocalContext.current)
    val locale = languageContext.resources.configuration.locales[0]
    val zoneId = ZoneId.systemDefault()
    return remember(instant, locale, zoneId) {
        formatDetailInstant(instant, locale, zoneId)
    }
}

internal fun formatDetailInstant(
    instant: Instant,
    locale: Locale,
    zoneId: ZoneId
): String {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.66f)
        )
    }
}

@Composable
private fun DetailChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = CodexBarSpacing.small, vertical = CodexBarSpacing.xsmall),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ServiceQuotaStatus.toDetailLabel(): String {
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
private fun ServiceQuotaStatus.statusColor(freshColor: Color): Color {
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
