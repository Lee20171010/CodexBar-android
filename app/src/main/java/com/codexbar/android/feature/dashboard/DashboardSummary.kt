package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus

/**
 * The single reading the dashboard opens with: how many providers are connected, how many need
 * attention, and which quota window runs out first.
 *
 * Kept free of Compose so the selection rules stay directly testable.
 */
data class DashboardSummary(
    val connectedCount: Int,
    val attentionCount: Int,
    val tightest: TightestQuota?,
    val pulses: List<ProviderPulse>
)

data class TightestQuota(
    val service: AiService,
    val metricLabel: String,
    val remainingLabel: String,
    val remainingPercent: Int?,
    val usedFraction: Double,
    val severity: QuotaSeverity,
    val resetLabel: String?,
    val paceLabel: String?
)

data class ProviderPulse(
    val service: AiService,
    val remainingPercent: Int?,
    val severity: QuotaSeverity,
    val needsAttention: Boolean
)

fun QuotaPresentationSnapshot.toDashboardSummary(): DashboardSummary {
    val tightestService = services
        .filter { it.status == ServiceQuotaStatus.Fresh }
        .mapNotNull { service ->
            val metric = service.metrics
                .filter { it.usedFraction != null }
                .maxByOrNull { it.usedFraction ?: 0.0 }
                ?: return@mapNotNull null
            service to metric
        }
        .maxByOrNull { (_, metric) -> metric.usedFraction ?: 0.0 }

    return DashboardSummary(
        connectedCount = services.size,
        attentionCount = services.count { it.needsAttention() },
        tightest = tightestService?.let { (service, metric) ->
            TightestQuota(
                service = service.service,
                metricLabel = metric.label,
                remainingLabel = metric.remainingLabel,
                remainingPercent = metric.remainingPercent,
                usedFraction = metric.usedFraction ?: 0.0,
                severity = metric.severity,
                resetLabel = metric.resetLabel,
                paceLabel = metric.resetPlan?.compactActionLabel
                    ?: metric.pace.label.takeIf { it.isNotBlank() }
            )
        },
        pulses = services.map { service ->
            ProviderPulse(
                service = service.service,
                remainingPercent = service.primaryMetric?.remainingPercent,
                severity = service.primaryMetric?.severity ?: QuotaSeverity.Unknown,
                needsAttention = service.needsAttention()
            )
        }
    )
}

internal fun ServiceQuotaPresentation.needsAttention(): Boolean {
    return status != ServiceQuotaStatus.Fresh && status != ServiceQuotaStatus.Redacted
}
