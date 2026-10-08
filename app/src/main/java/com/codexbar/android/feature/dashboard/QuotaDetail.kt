package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.balanceText
import com.codexbar.android.core.domain.model.spendText
import com.codexbar.android.core.presentation.QuotaPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun QuotaDetail(card: ServiceCardData, now: Instant, onRefresh: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier) {
    val snapshot = card.snapshot.retained(now)
    val quota = snapshot.quota
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(card.connection.name, style = MaterialTheme.typography.titleLarge)
        Text(listOfNotNull(card.service.displayName, quota?.tier).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        Text(QuotaPresentation.status(LocalContext.current, snapshot, now), style = MaterialTheme.typography.labelMedium)
        card.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        OfficialStatusText(card.officialStatus, now)
        quota?.let {
            it.windows.forEach { window ->
                if (window.supplemental) Text(stringResource(R.string.model_specific), style = MaterialTheme.typography.labelSmall)
                QuotaGaugeBar(window.utilization, Modifier.fillMaxWidth(), window.label, window.resetsAt, now)
                if (window.utilization > 1) Text(stringResource(R.string.over_quota), color = MaterialTheme.colorScheme.error)
            }
            it.money?.let { money ->
                HorizontalDivider()
                Text(money.balanceText())
                Text(money.spendText())
                Text(QuotaPresentation.age(LocalContext.current, money.fetchedAt, now), style = MaterialTheme.typography.labelSmall)
            }
            it.extraUsage?.let { extra ->
                Text(stringResource(R.string.reported_credits, extra.currency, extra.usedCredits, extra.monthlyLimit))
            }
            val measured = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it.fetchedAt)
            Text(stringResource(R.string.measurement_source, it.source, measured), style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
            TextButton(onClick = onSettings) { Text(stringResource(R.string.account_settings)) }
        }
    }
}

@Composable
internal fun OfficialStatusText(status: com.codexbar.android.core.data.OfficialStatus, now: Instant) {
    val indicator = if (status.isRecent(now.epochSecond)) status.indicator else "unknown"
    val label = stringResource(when (indicator) {
        "none" -> R.string.status_operational
        "minor", "major", "critical" -> R.string.status_incident
        "maintenance" -> R.string.status_maintenance
        else -> R.string.status_unknown
    })
    Text(stringResource(R.string.official_status, label), style = MaterialTheme.typography.labelMedium)
    if (status.isRecent(now.epochSecond)) status.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    status.source?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    if (status.checkedAt > 0) Text(stringResource(R.string.status_checked,
        QuotaPresentation.age(LocalContext.current, Instant.ofEpochSecond(status.checkedAt), now)),
        style = MaterialTheme.typography.labelSmall)
}
