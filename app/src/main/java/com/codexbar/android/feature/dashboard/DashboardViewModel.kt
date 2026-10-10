package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.presentation.Freshness
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
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
    private val officialStatus: com.codexbar.android.core.data.OfficialStatusRepository,
    private val widgets: WidgetPrefsManager,
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
            combine(prefsManager.connections, accounts.quotas, officialStatus.statuses, widgets.displayRevision, prefsManager.statusChecksEnabled) { connections, quotas, statuses, _, statusChecks ->
                val errors = mutableMapOf<AccountConnection, AppError>()
                val cards = connections.map { connection ->
                    val result = quotas[connection.id]?.takeIf {
                        it.connection.generation == connection.generation
                    }?.result
                    val snapshot = accounts.snapshot(connection)
                    val error = (result as? Result.Failure)?.error
                    if (error != null) errors[connection] = error
                    val card = snapshot.quota?.let { mapToCardData(connection, it) }
                        ?: ServiceCardData(connection, emptyList(), null, null)
                    card.copy(error = error, snapshot = snapshot,
                        officialStatus = if (statusChecks) statuses[connection.service] ?: com.codexbar.android.core.data.OfficialStatus() else null,
                        history = widgets.history(connection), display = widgets.effectiveDisplayOptions(connection))
                }
                when {
                    cards.isNotEmpty() && cards.all { it.isLoading } -> DashboardUiState.Loading
                    errors.isNotEmpty() -> DashboardUiState.PartialSuccess(cards, errors)
                    else -> DashboardUiState.Success(cards, connections.mapNotNull { connection ->
                        (quotas[connection.id]?.result as? Result.Success)?.value?.fetchedAt
                    }.maxOrNull() ?: Instant.now())
                }
            }.collect { _uiState.value = it }
        }
    }

    private var openedOnce = false

    fun refresh(connection: AccountConnection? = null) {
        if (refreshJob?.isActive == true) return
        val connections = connection?.let(::listOf) ?: prefsManager.loadConnections()
        refreshAll(connections, concurrent = true)
    }

    /**
     * Mac refreshAllProvidersOnMenuOpen (default OFF, SettingsStore.swift:545): the first open always
     * fetches; afterwards ON refreshes every eligible account concurrently, OFF only recovers accounts
     * whose last attempt failed or that hold no quota, sequentially like Mac's MenuOpenRefreshPlan.
     * Terminal-reconnect accounts are skipped on automatic paths as QuotaRefreshWorker.kt:38-41 does;
     * manual refresh() still attempts them. Official status checks stay independent of quota eligibility.
     */
    fun onDashboardOpen() {
        val firstOpen = !openedOnce
        openedOnce = true
        if (refreshJob?.isActive == true) return
        val connections = prefsManager.loadConnections()
        val eligible = connections.filterNot { accounts.snapshot(it).freshness() == Freshness.RECONNECT }
        val refreshOnOpen = prefsManager.getRefreshOnOpen()
        val targets = when {
            firstOpen || refreshOnOpen -> eligible
            else -> eligible.filter { accounts.snapshot(it).let { s -> s.failure != null || s.quota == null } }
        }
        refreshAll(targets, concurrent = firstOpen || refreshOnOpen,
            statusServices = connections.map { it.service }.distinct())
    }

    private fun refreshAll(
        connections: List<AccountConnection>,
        concurrent: Boolean,
        statusServices: List<AiService> = connections.map { it.service }.distinct()
    ) {
        viewModelScope.launch {
            statusServices.forEach { officialStatus.refresh(it) }
        }
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (concurrent) connections.map { account -> async { accounts.refresh(account) } }.awaitAll()
                else connections.forEach { account -> accounts.refresh(account) }
                updateQuotaSurfaces(context)
            } catch (_: IOException) {
                _uiState.value = DashboardUiState.Error(AppError.ParseError("Account storage unavailable. Restart the app."))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setDisplay(connection: AccountConnection, options: com.codexbar.android.core.presentation.DisplayOptions, touched: String) {
        prefsManager.publishIfCurrent(connection) { widgets.saveDisplayOptions(connection, options, touched = setOf(touched)) }
        viewModelScope.launch { updateQuotaSurfaces(context) }
    }

    /** Restore defaults means inheriting again, not freezing the built-in defaults. */
    fun clearDisplay(connection: AccountConnection) {
        prefsManager.publishIfCurrent(connection) { widgets.clearDisplayOptions(connection) }
        viewModelScope.launch { updateQuotaSurfaces(context) }
    }

    private fun mapToCardData(connection: AccountConnection, quota: QuotaInfo) = ServiceCardData(
        connection = connection,
        windows = quota.windows.map { UsageWindowUi(it.label, it.utilization, it.resetsAt) },
        extraUsage = quota.extraUsage?.let { ExtraUsageUi(it.monthlyLimit, it.usedCredits, it.utilization, it.currency) },
        tier = quota.tier,
        money = quota.money
    )
}
