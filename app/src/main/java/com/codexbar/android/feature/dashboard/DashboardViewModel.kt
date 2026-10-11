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
            val cardStates = combine(prefsManager.connections, accounts.quotas, officialStatus.statuses, widgets.displayRevision, prefsManager.statusChecksEnabled) { connections, quotas, statuses, _, statusChecks ->
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
            }
            combine(prefsManager.multiAccountSegmented, prefsManager.displayedConnections, cardStates) { segmented, pointers, state ->
                when (state) {
                    is DashboardUiState.Success -> state.copy(
                        segmented = segmented, displayedIds = displayedIds(state.cards, pointers))
                    is DashboardUiState.PartialSuccess -> state.copy(
                        segmented = segmented, displayedIds = displayedIds(state.cards, pointers))
                    else -> state
                }
            }.collect { _uiState.value = it }
        }
    }

    /** Display-only selection: stored pointer, else the legacy default account, else first added.
     * Never labelled "Active" — this pointer does not own credentials. All cards stay in state. */
    private fun pick(cards: List<ServiceCardData>, pointers: Map<String, String>): String? {
        val service = cards.firstOrNull()?.service ?: return null
        return (cards.firstOrNull { it.connection.id == pointers[service.name] }
            ?: cards.firstOrNull { it.connection.id == service.name }
            ?: cards.first()).connection.id
    }

    private fun displayedIds(cards: List<ServiceCardData>, pointers: Map<String, String>): Set<String> =
        cards.groupBy { it.service }.mapNotNull { pick(it.value, pointers) }.toSet()

    /** Chip switch: persists the display pointer then refreshes just that account, mirroring Mac's
     * activate-cached-snapshot-then-refresh (StatusItemController+Menu.swift:1058-1135). Separate from
     * refreshJob so an in-flight full refresh never drops a switch; the coordinator's per-account mutex
     * and attemptedAt dedupe handle overlapping switches. */
    fun setDisplayed(connection: AccountConnection) {
        val cards = when (val state = _uiState.value) {
            is DashboardUiState.Success -> state.cards
            is DashboardUiState.PartialSuccess -> state.cards
            else -> return
        }
        val group = cards.filter { it.service == connection.service }
        if (pick(group, prefsManager.displayedConnections.value) == connection.id) return
        val stored = try {
            // Pointer persists synchronously (like setDisplay) so a rapid second switch is not
            // misread as a no-op before the first launch runs. The setter validates currency on
            // its own monitor; false means the account was removed or replaced in the meantime.
            prefsManager.setDisplayedConnection(connection)
        } catch (_: IOException) {
            _uiState.value = DashboardUiState.Error(AppError.ParseError("Account storage unavailable. Restart the app."))
            return
        }
        if (!stored) return
        viewModelScope.launch {
            try {
                if (accounts.snapshot(connection).freshness() != Freshness.RECONNECT) accounts.refresh(connection)
                updateQuotaSurfaces(context)
            } catch (_: IOException) {
                _uiState.value = DashboardUiState.Error(AppError.ParseError("Account storage unavailable. Restart the app."))
            }
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
