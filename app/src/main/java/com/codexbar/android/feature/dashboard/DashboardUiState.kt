package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ReportedMoney
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.presentation.WindowHistory
import java.time.Instant

sealed class DashboardUiState {
    data object Loading : DashboardUiState()

    data class Success(
        val cards: List<ServiceCardData>,
        val lastUpdated: Instant
    ) : DashboardUiState()

    data class PartialSuccess(
        val cards: List<ServiceCardData>,
        val errors: Map<AccountConnection, AppError>
    ) : DashboardUiState()

    data class Error(val error: AppError) : DashboardUiState()
}

data class ServiceCardData(
    val connection: AccountConnection,
    val windows: List<UsageWindowUi>,
    val extraUsage: ExtraUsageUi?,
    val tier: String?,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val money: ReportedMoney? = null,
    val snapshot: QuotaSnapshot = QuotaSnapshot(),
    val officialStatus: com.codexbar.android.core.data.OfficialStatus = com.codexbar.android.core.data.OfficialStatus(),
    val history: Map<String, WindowHistory> = emptyMap()
) {
    val service: AiService get() = connection.service
}

data class UsageWindowUi(
    val label: String,
    val utilization: Double,
    val resetsAt: Instant? = null
)

data class ExtraUsageUi(
    val monthlyLimit: Double,
    val usedCredits: Double,
    val utilization: Double,
    val currency: String
)
