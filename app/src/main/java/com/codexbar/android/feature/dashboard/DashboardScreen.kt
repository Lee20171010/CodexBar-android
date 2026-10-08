package com.codexbar.android.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    val selected = cards.find { it.connection.id == selectedId }
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
                        CardList(cards, now, { selectedId = it.connection.id }, Modifier.weight(1f).fillMaxHeight())
                        if (wide && selected != null) {
                            VerticalDivider()
                            QuotaDetail(selected, now, { viewModel.refresh(selected.connection) }, onNavigateToSettings,
                                Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    if (!wide && selected != null) {
                        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
                            QuotaDetail(selected, now, { viewModel.refresh(selected.connection) }, onNavigateToSettings,
                                Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardList(cards: List<ServiceCardData>, now: Instant, onSelect: (ServiceCardData) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.connection.id }) { card ->
            ServiceCard(card, { onSelect(card) }, Modifier.fillMaxWidth(), now)
        }
    }
}
