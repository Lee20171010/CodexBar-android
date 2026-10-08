package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val accent = Color(cardData.service.brandColor)
    Card(onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Mac menu-card header: name (headline semibold) + identity line (subheadline
            // secondary) + plan badge (footnote semibold), 4dp line spacing, 12dp columns.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cardData.connection.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(cardData.service.displayName, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                quota?.tier?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            quota?.let {
                cardData.display.visible(it).filterNot { it.supplemental }.forEach { window ->
                    QuotaGaugeBar(window.utilization, Modifier.fillMaxWidth(), window.label, window.resetsAt, now,
                        cardData.display, accent)
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
