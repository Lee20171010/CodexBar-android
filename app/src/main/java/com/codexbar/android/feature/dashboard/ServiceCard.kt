package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.balanceText
import com.codexbar.android.core.presentation.QuotaPresentation
import java.time.Instant

@Composable
fun ServiceCard(
    cardData: ServiceCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now()
) {
    val snapshot = cardData.snapshot.retained(now)
    val quota = snapshot.quota
    Card(onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(cardData.service.brandColor), modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(cardData.connection.name, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(cardData.service.displayName, quota?.tier).distinct().joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            quota?.let {
                cardData.display.visible(it).filterNot { it.supplemental }.forEach { window ->
                    QuotaGaugeBar(window.utilization, Modifier.fillMaxWidth(), window.label, window.resetsAt, now, cardData.display)
                }
                if (cardData.display.hiddenRisk(it)) Text(stringResource(R.string.hidden_quota_risk), color = MaterialTheme.colorScheme.error)
                if (it.windows.none { window -> !window.supplemental } && cardData.display.showAmounts) {
                    Text(it.money?.balanceText() ?: stringResource(R.string.quota_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(QuotaPresentation.status(LocalContext.current, snapshot, now), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            cardData.error?.let { Text(errorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (cardData.officialStatus.hasIncident(now.epochSecond)) OfficialStatusText(cardData.officialStatus, now)
        }
    }
}

@Composable
internal fun errorText(error: AppError): String = stringResource(when (error) {
    is AppError.NetworkError -> R.string.network_error
    is AppError.AuthError -> if (error.isTerminal) R.string.reconnect_required else R.string.authentication_pending
    is AppError.RateLimited -> R.string.rate_limited
    is AppError.ParseError -> R.string.response_error
    is AppError.CredentialNotFound -> R.string.reconnect_required
    is AppError.ServiceUnavailable -> R.string.service_unavailable
})
