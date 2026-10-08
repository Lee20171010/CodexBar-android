package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.updateQuotaSurfaces
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accounts: AccountQuotaCoordinator,
    private val prefsManager: EncryptedPrefsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private var refreshJob: Job? = null

    init {
        prefsManager.loadConnections()
        viewModelScope.launch {
            combine(prefsManager.connections, accounts.quotas) { connections, quotas ->
                val errors = mutableMapOf<AccountConnection, AppError>()
                val cards = connections.map { connection ->
                    val result = quotas[connection.id]?.takeIf {
                        it.connection.generation == connection.generation
                    }?.result
                    when (result) {
                        is Result.Success -> mapToCardData(connection, result.value)
                        is Result.Failure -> {
                            errors[connection] = result.error
                            ServiceCardData(connection, emptyList(), null, null, error = result.error)
                        }
                        null -> ServiceCardData(connection, emptyList(), null, null, isLoading = true)
                    }
                }.sortedByDescending { card -> card.windows.maxOfOrNull { it.utilization } ?: 0.0 }
                when {
                    cards.isNotEmpty() && cards.all { it.isLoading } -> DashboardUiState.Loading
                    errors.isNotEmpty() -> DashboardUiState.PartialSuccess(cards, errors)
                    else -> DashboardUiState.Success(cards, connections.mapNotNull { connection ->
                        (quotas[connection.id]?.result as? Result.Success)?.value?.fetchedAt
                    }.maxOrNull() ?: Instant.now())
                }
            }.collect { _uiState.value = it }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                prefsManager.loadConnections().map { connection ->
                    async { accounts.refresh(connection) }
                }.awaitAll()
                updateQuotaSurfaces(context)
            } catch (_: IOException) {
                _uiState.value = DashboardUiState.Error(AppError.ParseError("Account storage unavailable. Restart the app."))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun mapToCardData(connection: AccountConnection, quota: QuotaInfo) = ServiceCardData(
        connection = connection,
        windows = quota.windows.map { UsageWindowUi(it.label, it.utilization, it.resetsAt) },
        extraUsage = quota.extraUsage?.let { ExtraUsageUi(it.monthlyLimit, it.usedCredits, it.utilization, it.currency) },
        tier = quota.tier,
        money = quota.money
    )
}
