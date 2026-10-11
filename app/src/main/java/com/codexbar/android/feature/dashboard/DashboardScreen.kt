package com.codexbar.android.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.codexbar.android.R
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigateToSettings: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val owner = LocalLifecycleOwner.current
    LifecycleResumeEffect(Unit) {
        viewModel.onDashboardOpen()
        onPauseOrDispose { }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = Instant.now(); delay(60_000) }
        }
    }
    val cards = when (val current = state) {
        is DashboardUiState.Success -> current.cards
        is DashboardUiState.PartialSuccess -> current.cards
        else -> emptyList()
    }
    val segmented = when (val current = state) {
        is DashboardUiState.Success -> current.segmented
        is DashboardUiState.PartialSuccess -> current.segmented
        else -> false
    }
    val displayedIds = when (val current = state) {
        is DashboardUiState.Success -> current.displayedIds
        is DashboardUiState.PartialSuccess -> current.displayedIds
        else -> emptySet()
    }
    // All cards stay in state; segmented mode only filters what the list shows.
    val visible = if (segmented) cards.filter { it.connection.id in displayedIds } else cards
    LaunchedEffect(segmented, displayedIds, cards) {
        if (selectedId != null && visible.none { it.connection.id == selectedId }) selectedId = null
    }
    val selected = visible.find { it.connection.id == selectedId }
    BackHandler(selected != null) { selectedId = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
            IconButton(onClick = { viewModel.refresh() }, enabled = !refreshing) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            }
            IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        })
    }) { padding ->
        PullToRefreshBox(refreshing, { viewModel.refresh() }, Modifier.fillMaxSize().padding(padding)) {
            if (state is DashboardUiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (cards.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (state is DashboardUiState.Error) R.string.response_error else R.string.no_accounts))
                    Button(onClick = onNavigateToSettings) { Text(stringResource(R.string.account_settings)) }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 840.dp
                    Row(Modifier.fillMaxSize()) {
                        CardList(visible,
                            if (segmented) cards.groupBy { it.service }.filterValues { it.size > 1 } else emptyMap(),
                            now, { selectedId = it.connection.id },
                            { viewModel.setDisplayed(it.connection) }, Modifier.weight(1f).fillMaxHeight())
                        if (wide && selected != null) {
                            VerticalDivider()
                            QuotaDetail(selected, now, { viewModel.refresh(selected.connection) }, onNavigateToSettings,
                                 Modifier.weight(1f).fillMaxHeight(), { options, field -> viewModel.setDisplay(selected.connection, options, field) },
                                 { viewModel.clearDisplay(selected.connection) })
                        }
                    }
                    if (!wide && selected != null) {
                        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
                            QuotaDetail(selected, now, { viewModel.refresh(selected.connection) }, onNavigateToSettings,
                                 Modifier.fillMaxWidth(), { options, field -> viewModel.setDisplay(selected.connection, options, field) },
                                 { viewModel.clearDisplay(selected.connection) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardList(
    cards: List<ServiceCardData>,
    chips: Map<com.codexbar.android.core.domain.model.AiService, List<ServiceCardData>>,
    now: Instant,
    onSelect: (ServiceCardData) -> Unit,
    onChip: (ServiceCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.connection.id }) { card ->
            val group = chips[card.service]
            if (group == null) {
                ServiceCard(card, { onSelect(card) }, Modifier.fillMaxWidth(), now)
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { option ->
                            // Localized text, not color-only, so hidden chips stay accessible.
                            val attention = when {
                                option.snapshot.freshness() == com.codexbar.android.core.presentation.Freshness.RECONNECT ->
                                    stringResource(R.string.reconnect_required)
                                option.error != null -> stringResource(R.string.account_needs_attention)
                                else -> null
                            }
                            FilterChip(
                                selected = option.connection.id == card.connection.id,
                                onClick = { onChip(option) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(option.connection.name + (attention?.let { " · $it" } ?: "")) }
                            )
                        }
                    }
                    ServiceCard(card, { onSelect(card) }, Modifier.fillMaxWidth(), now)
                }
            }
        }
    }
}
