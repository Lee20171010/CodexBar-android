package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

private const val TwoPaneMinWidthDp = 720f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToConnections: () -> Unit,
    initialSelectedService: AiService? = null,
    onInitialSelectionConsumed: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    DashboardContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onNavigateToConnections = onNavigateToConnections,
        initialSelectedService = initialSelectedService,
        onInitialSelectionConsumed = onInitialSelectionConsumed
    )
}

@Composable
fun DashboardPreviewScreen(
    snapshot: QuotaPresentationSnapshot,
    onNavigateToConnections: () -> Unit = {}
) {
    DashboardContent(
        uiState = DashboardUiState.Content(snapshot),
        isRefreshing = false,
        onRefresh = {},
        onNavigateToConnections = onNavigateToConnections
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToConnections: () -> Unit,
    initialSelectedService: AiService? = null,
    onInitialSelectionConsumed: () -> Unit = {}
) {
    val themeProfile = LocalCodexBarThemeProfile.current
    var selectedServiceName by remember { mutableStateOf<String?>(null) }

    // A notification or Now Bar entry names the provider it was opened for.
    LaunchedEffect(initialSelectedService) {
        if (initialSelectedService != null) {
            selectedServiceName = initialSelectedService.name
            onInitialSelectionConsumed()
        }
    }

    val explicitlySelectedService = (uiState as? DashboardUiState.Content)
        ?.snapshot
        ?.services
        ?.firstOrNull { it.service.name == selectedServiceName }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeProfile.topBarContainerColor
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val useTwoPane = useTwoPaneDashboard(maxWidth.value)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is DashboardUiState.Content -> {
                        if (state.snapshot.services.isEmpty()) {
                            EmptyState(onOpenConnections = onNavigateToConnections)
                        } else {
                            val failedServices = state.snapshot.services
                                .filterNot {
                                    it.status == ServiceQuotaStatus.Fresh ||
                                        it.status == ServiceQuotaStatus.Redacted
                                }
                                .joinToString(", ") { it.service.displayName }
                            val errorBanner = failedServices.takeIf { it.isNotBlank() }
                                ?.let {
                                    stringResource(R.string.dashboard_needs_attention, it)
                                }
                            val paneService = explicitlySelectedService
                                ?: state.snapshot.services.first()

                            if (useTwoPane) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    CardList(
                                        services = state.snapshot.services,
                                        errorBanner = errorBanner,
                                        selectedServiceName = paneService.service.name,
                                        onServiceClick = {
                                            selectedServiceName = it.service.name
                                        },
                                        modifier = Modifier.weight(0.46f)
                                    )
                                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                                    Box(
                                        modifier = Modifier
                                            .weight(0.54f)
                                            .fillMaxHeight()
                                            .padding(CodexBarSpacing.large)
                                    ) {
                                        ServiceDetailPane(
                                            service = paneService,
                                            onRefresh = onRefresh,
                                            onManageConnection = onNavigateToConnections
                                        )
                                    }
                                }
                            } else {
                                CardList(
                                    services = state.snapshot.services,
                                    errorBanner = errorBanner,
                                    selectedServiceName = null,
                                    onServiceClick = {
                                        selectedServiceName = it.service.name
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!useTwoPane) {
                explicitlySelectedService?.let { service ->
                    ServiceDetailSheet(
                        service = service,
                        onDismiss = { selectedServiceName = null },
                        onRefresh = onRefresh,
                        onManageConnection = {
                            selectedServiceName = null
                            onNavigateToConnections()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardList(
    services: List<ServiceQuotaPresentation>,
    errorBanner: String?,
    selectedServiceName: String?,
    onServiceClick: (ServiceQuotaPresentation) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (errorBanner != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        items(services, key = { it.service.name }) { service ->
            ServiceCard(
                service = service,
                onClick = { onServiceClick(service) },
                selected = service.service.name == selectedServiceName
            )
        }
    }
}

@Composable
private fun EmptyState(onOpenConnections: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onOpenConnections) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_connections))
            }
        }
    }
}

internal fun useTwoPaneDashboard(widthDp: Float): Boolean {
    return widthDp >= TwoPaneMinWidthDp
}
