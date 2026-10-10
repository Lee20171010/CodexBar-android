package com.codexbar.android.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.BuildConfig
import com.codexbar.android.R
import com.codexbar.android.core.network.codex.CodexDeviceAuth
import com.codexbar.android.core.network.copilot.CopilotDeviceAuth

private enum class SettingsPage(val titleRes: Int) {
    GENERAL(R.string.settings_general),
    NOTIFICATIONS(R.string.settings_notifications),
    MENU(R.string.settings_menu),
    SPEND(R.string.settings_spend),
    ADVANCED(R.string.settings_advanced),
    ABOUT(R.string.settings_about)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Mac sidebar: one destination per provider, mutually exclusive with app panes.
    var provider by rememberSaveable { mutableStateOf<AiService?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var renameAccount by remember { mutableStateOf<AccountConnection?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteAccount by remember { mutableStateOf<AccountConnection?>(null) }
    DisposableEffect(viewModel) {
        onDispose { AiService.entries.forEach(viewModel::cancelDraft) }
    }

    val current = page
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider?.displayName ?: stringResource(current?.titleRes ?: R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (current == null && provider == null) onNavigateBack()
                        else { page = null; provider = null }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Wide screens restore the Mac sidebar + detail layout.
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SettingsDirectory(
                        selected = if (provider == null) current ?: SettingsPage.GENERAL else null,
                        selectedProvider = provider,
                        onSelect = { page = it; provider = null },
                        onSelectProvider = { provider = it; page = null },
                        modifier = Modifier.weight(0.38f).fillMaxHeight()
                    )
                    VerticalDivider(Modifier.fillMaxHeight())
                    SettingsPageContent(
                        page = current ?: SettingsPage.GENERAL,
                        provider = provider,
                        uiState = uiState,
                        viewModel = viewModel,
                        onAboutLicenses = { showAbout = true },
                        onRename = { renameAccount = it; renameText = it.name },
                        onDelete = { deleteAccount = it },
                        modifier = Modifier.weight(0.62f).fillMaxHeight()
                    )
                }
            } else if (current == null && provider == null) {
                SettingsDirectory(
                    selected = null, selectedProvider = null,
                    onSelect = { page = it }, onSelectProvider = { provider = it },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BackHandler { page = null; provider = null }
                SettingsPageContent(
                    page = current ?: SettingsPage.GENERAL,
                    provider = provider,
                    uiState = uiState,
                    viewModel = viewModel,
                    onAboutLicenses = { showAbout = true },
                    onRename = { renameAccount = it; renameText = it.name },
                    onDelete = { deleteAccount = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    renameAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { renameAccount = null },
            title = { Text("Rename account") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Account name") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty() && renameText.trim().length <= 80 && renameText.none(Char::isISOControl),
                    onClick = { viewModel.rename(account, renameText); renameAccount = null }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameAccount = null }) { Text("Cancel") } }
        )
    }
    deleteAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteAccount = null },
            title = { Text("Delete ${account.name}?") },
            text = { Text("Removes this account's credentials and cached data. Widgets pinned to it will show account unavailable.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(account); deleteAccount = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteAccount = null }) { Text("Cancel") } }
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteAllCredentials()
                viewModel.dismissDeleteConfirmDialog()
            },
            onDismiss = { viewModel.dismissDeleteConfirmDialog() }
        )
    }
}

@Composable
private fun SettingsDirectory(
    selected: SettingsPage?,
    selectedProvider: AiService?,
    onSelect: (SettingsPage) -> Unit,
    onSelectProvider: (AiService) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Native SettingsSidebarView orders app panes before provider settings.
        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )) {
            listOf(SettingsPage.GENERAL, SettingsPage.SPEND, SettingsPage.NOTIFICATIONS,
                SettingsPage.MENU, SettingsPage.ADVANCED, SettingsPage.ABOUT).forEach { entry ->
                SettingsDirectoryRow(entry, selected == entry, onSelect)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_providers),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )) {
                AiService.entries.filter { !it.usesNativeApiKey || BuildConfig.NATIVE_CLI_ENABLED }.forEach { service ->
                    SettingsProviderDirectoryRow(service, selectedProvider == service) { onSelectProvider(service) }
                }
            }
        }
    }
}

@Composable
private fun SettingsProviderDirectoryRow(service: AiService, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null,
            tint = Color(service.brandColor), modifier = Modifier.size(24.dp))
        Text(service.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsDirectoryRow(entry: SettingsPage, selected: Boolean, onSelect: (SettingsPage) -> Unit) {
    val (icon, tint) = when (entry) {
        SettingsPage.GENERAL -> Icons.Default.Settings to Color(0xFF8E8E93)
        SettingsPage.SPEND -> Icons.Default.BarChart to Color(0xFF248A3D)
        SettingsPage.NOTIFICATIONS -> Icons.Default.Notifications to Color(0xFFD70015)
        SettingsPage.MENU -> Icons.Default.Menu to Color(0xFF007F86)
        SettingsPage.ADVANCED -> Icons.Default.Tune to Color(0xFF8944AB)
        SettingsPage.ABOUT -> Icons.Default.Info to Color(0xFF248A3D)
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(entry) })
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(24.dp).background(tint, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(stringResource(entry.titleRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    provider: AiService?,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onAboutLicenses: () -> Unit,
    onRename: (AccountConnection) -> Unit,
    onDelete: (AccountConnection) -> Unit,
    modifier: Modifier = Modifier
) {
    // Route changes start at the top, like the Mac pane swap.
    val scrollState = rememberScrollState()
    LaunchedEffect(page, provider) { scrollState.scrollTo(0) }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            provider != null -> AccountsPage(provider, uiState, viewModel, onRename, onDelete)
            page == SettingsPage.GENERAL -> {
                RefreshIntervalSection(
                    currentMinutes = uiState.refreshIntervalMinutes,
                    onIntervalChange = { viewModel.setRefreshInterval(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.status_checks_setting),
                    description = stringResource(R.string.status_checks_setting_summary),
                    enabled = uiState.statusChecksEnabled,
                    gateOnNotificationPermission = false,
                    onToggle = { viewModel.setStatusChecksEnabled(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.refresh_on_open_title),
                    description = stringResource(R.string.refresh_on_open_summary),
                    enabled = uiState.refreshOnOpen,
                    gateOnNotificationPermission = false,
                    onToggle = { viewModel.setRefreshOnOpen(it) }
                )
                LanguageSection()
            }
            page == SettingsPage.NOTIFICATIONS -> {
                ToggleSection(
                    title = stringResource(R.string.notification_status_title),
                    description = stringResource(R.string.notification_status_description),
                    enabled = uiState.notificationsEnabled,
                    onToggle = { viewModel.setNotificationsEnabled(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.recovery_setting_title),
                    description = stringResource(R.string.recovery_setting_description),
                    enabled = uiState.recoveryAlertsEnabled,
                    onToggle = { viewModel.setRecoveryAlertsEnabled(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.alert_depleted_setting),
                    description = stringResource(R.string.alert_depleted_setting_summary),
                    enabled = uiState.alertDepletedEnabled,
                    onToggle = { viewModel.setAlertDepletedEnabled(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.alert_low_setting),
                    description = stringResource(R.string.alert_low_setting_summary),
                    enabled = uiState.alertLowEnabled,
                    onToggle = { viewModel.setAlertLowEnabled(it) }
                )
                ToggleSection(
                    title = stringResource(R.string.alert_pace_setting),
                    description = stringResource(R.string.alert_pace_setting_summary),
                    enabled = uiState.alertPaceEnabled,
                    onToggle = { viewModel.setAlertPaceEnabled(it) }
                )
            }
            page == SettingsPage.MENU -> DisplayPrefsSection(
                display = uiState.globalDisplay,
                onChange = { viewModel.setGlobalDisplay(it) }
            )
            page == SettingsPage.SPEND -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.spend_unavailable_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.spend_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            page == SettingsPage.ADVANCED -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.advanced_platform_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.advanced_platform_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Global credential wipe stays on Advanced, away from individual provider pages.
                DangerZoneSection(onDeleteAll = { viewModel.showDeleteConfirmDialog() })
            }
            page == SettingsPage.ABOUT -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.unofficial_port), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.about_versions, BuildConfig.VERSION_NAME, BuildConfig.CORE_CLI_VERSION),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/Lee20171010/CodexBar-android") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_source)) }
                TextButton(onClick = onAboutLicenses) {
                    Text(stringResource(R.string.about_licenses))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AccountsPage(
    service: AiService,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onRename: (AccountConnection) -> Unit,
    onDelete: (AccountConnection) -> Unit
) {
    uiState.connections.filter { it.service == service }.forEach { account ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                Text(account.service.displayName, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onRename(account) }) { Text("Rename") }
                    TextButton(onClick = { viewModel.beginReconnect(account) }) { Text("Reconnect") }
                    TextButton(onClick = { onDelete(account) }) { Text("Delete") }
                }
                ProviderDisplaySection(account, viewModel)
            }
        }
    }
    val state = uiState.serviceStates[service] ?: ServiceCredentialState()
    if (state.connection == null) OutlinedButton(
        onClick = { viewModel.beginAdd(service) }, modifier = Modifier.fillMaxWidth()
    ) { Text("Add ${service.displayName} account") }
    else ServiceCredentialSection(
        service = service,
        state = state,
        onFieldChange = { field, value -> viewModel.updateField(service, field, value) },
        onValidate = { viewModel.validateCredential(service) },
        onSignIn = { viewModel.signIn(service) },
        onCancel = { viewModel.cancelDraft(service) }
    )
}

@Composable
private fun ServiceCredentialSection(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onValidate: () -> Unit,
    onSignIn: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = service.displayName,
                    tint = Color(service.brandColor),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(if (state.previous == null) "New account" else "Reconnect ${state.previous.name}")
            OutlinedTextField(
                value = state.name,
                onValueChange = { onFieldChange("name", it) },
                label = { Text("Account name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (service == AiService.COPILOT && BuildConfig.COPILOT_CLIENT_ID.isEmpty()) {
                Text(stringResource(R.string.copilot_registration_pending), style = MaterialTheme.typography.bodySmall)
            }
            if (service == AiService.CODEX || (service == AiService.COPILOT && BuildConfig.COPILOT_CLIENT_ID.isNotEmpty())) {
                val verificationUrl = if (service == AiService.CODEX) CodexDeviceAuth.VERIFICATION_URL else CopilotDeviceAuth.VERIFICATION_URL
                val uriHandler = LocalUriHandler.current
                var browserUnavailable by remember { mutableStateOf(false) }
                OutlinedButton(onClick = onSignIn, enabled = !state.isValidating) {
                    Text(if (service == AiService.CODEX) "Sign in with ChatGPT" else stringResource(R.string.sign_in_github))
                }
                Text(if (service == AiService.CODEX) "Enable device-code sign-in in ChatGPT security settings if required. Authorize in your browser; this app never asks for your password."
                    else stringResource(R.string.github_device_help),
                    style = MaterialTheme.typography.bodySmall)
                state.deviceUserCode?.let { code ->
                    SelectionContainer { Text("Device code: $code", style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(onClick = {
                        browserUnavailable = try {
                            uriHandler.openUri(verificationUrl)
                            false
                        } catch (_: IllegalArgumentException) { true }
                    }) { Text("Open authorization page") }
                    Text("Waiting for authorization. Cancel to stop.")
                    if (browserUnavailable) Text("Open $verificationUrl in your browser.")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Or enter existing tokens manually", style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { onFieldChange("accessToken", it) },
                label = { Text(if (service == AiService.COPILOT) "GitHub OAuth token" else if (service.usesApiKey) "${service.displayName} API Key" else "Access Token") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (service.usesApiKey) {
                    { Text(if (service == AiService.COPILOT) "Use a GitHub OAuth token with Copilot access, not a short-lived Copilot session token. Stored encrypted. Enterprise hosts are not supported."
                        else "Paste your ${service.displayName} API key. Stored encrypted; only the provider API is queried.") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!service.usesApiKey) OutlinedTextField(
                value = state.refreshToken,
                onValueChange = { onFieldChange("refreshToken", it) },
                label = { Text("Refresh Token") },
                supportingText = if (service == AiService.CLAUDE) {
                    { Text("Required for auto-refresh (tokens expire every 8h)") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Service-specific fields
            when (service) {
                AiService.CODEX -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.accountId,
                        onValueChange = { onFieldChange("accountId", it) },
                        label = { Text("Account ID (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                AiService.GEMINI -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.oauthClientId,
                        onValueChange = { onFieldChange("oauthClientId", it) },
                        label = { Text("OAuth Client ID") },
                        supportingText = { Text("From Google Cloud Console") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.oauthClientSecret,
                        onValueChange = { onFieldChange("oauthClientSecret", it) },
                        label = { Text("OAuth Client Secret") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (state.expiresAtDisplay.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.expiresAtDisplay,
                            onValueChange = {},
                            label = { Text("Token Expiry") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                            singleLine = true
                        )
                    }
                }
                else -> {} // Claude has no extra fields
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Validate button + result
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.accessToken.isNotBlank()
                ) {
                    if (state.isValidating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Validate & save")
                }

                Spacer(modifier = Modifier.width(12.dp))

                when (state.validationResult) {
                    is ValidationResult.Success -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Valid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                    is ValidationResult.Failure -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Invalid",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            state.validationResult.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    null -> {}
                }
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIntervalSection(
    currentMinutes: Long,
    onIntervalChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.general_refresh_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            val options = listOf(
                15L to stringResource(R.string.general_refresh_15),
                30L to stringResource(R.string.general_refresh_30),
                60L to stringResource(R.string.general_refresh_60),
                0L to stringResource(R.string.general_refresh_manual)
            )
            val selectedIndex = options.indexOfFirst { it.first == currentMinutes }.takeIf { it >= 0 } ?: 1

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (minutes, label) ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onIntervalChange(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

/**
 * Honest Android equivalent of the Mac language picker: the per-app language
 * system settings screen (API 33+). Hidden where the platform has no per-app locale.
 */
@Composable
private fun LanguageSection() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                                    .addCategory(android.content.Intent.CATEGORY_DEFAULT)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            )
                        }.onFailure { unavailable = true }
                    }
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.general_language), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.general_language_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            if (unavailable) Text(
                stringResource(R.string.general_language_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

/**
 * Per-connection display overrides on the provider page, mirroring the Mac
 * provider detail (headline/percent window + accent). State follows the shared
 * display revision, so global or dashboard edits never leave stale values here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderDisplaySection(account: AccountConnection, viewModel: SettingsViewModel) {
    val revision by viewModel.displayRevision.collectAsStateWithLifecycle()
    val display = remember(account.id, revision) { viewModel.connectionDisplay(account) }
    val windows = viewModel.cachedWindows(account)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.provider_display_title), style = MaterialTheme.typography.titleSmall)
    if (windows.isNotEmpty()) {
        Text(stringResource(R.string.provider_headline_title), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = display.headlineWindow == null,
                onClick = { viewModel.setConnectionDisplay(account, display.copy(headlineWindow = null), "headlineWindow") },
                label = { Text(stringResource(R.string.provider_headline_auto)) }
            )
            windows.forEach { window ->
                FilterChip(
                    selected = display.headlineWindow == window.id,
                    onClick = { viewModel.setConnectionDisplay(account, display.copy(headlineWindow = window.id), "headlineWindow") },
                    label = { Text(window.label) }
                )
            }
        }
    }
    Text(stringResource(R.string.provider_accent_title), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = display.accent == null,
            onClick = { viewModel.setConnectionDisplay(account, display.copy(accent = null), "accent") },
            label = { Text(stringResource(R.string.provider_accent_default)) }
        )
        com.codexbar.android.feature.dashboard.ACCENT_COLORS.forEach { (key, color) ->
            val label = stringResource(com.codexbar.android.feature.dashboard.accentLabelRes(key))
            // 48dp selectable target with localized label; the 28dp swatch is decoration inside it.
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = display.accent == key, role = Role.RadioButton,
                        onClick = { viewModel.setConnectionDisplay(account, display.copy(accent = key), "accent") })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(color, CircleShape)
                        .border(
                            if (display.accent == key) 2.dp else 0.dp,
                            MaterialTheme.colorScheme.onSurface,
                            CircleShape
                        )
                )
            }
        }
    }
    TextButton(onClick = { viewModel.resetConnectionDisplay(account) }) { Text(stringResource(R.string.restore_defaults)) }
}

/**
 * Global display defaults, the Mac menu-bar preferences equivalent.
 * Per-connection overrides in the dashboard detail win over these.
 */
@Composable
private fun DisplayPrefsSection(
    display: com.codexbar.android.core.presentation.DisplayOptions,
    onChange: (com.codexbar.android.core.presentation.DisplayOptions) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.display_fill_title), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !display.fillUsed,
                    onClick = { onChange(display.copy(fillUsed = false)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.display_fill_remaining)) }
                SegmentedButton(
                    selected = display.fillUsed,
                    onClick = { onChange(display.copy(fillUsed = true)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.display_fill_used)) }
            }
            PrefsSwitchRow(
                checked = display.showMarkers,
                title = stringResource(R.string.display_markers_title),
                summary = stringResource(R.string.display_markers_summary),
                onToggle = { onChange(display.copy(showMarkers = it)) }
            )
            PrefsSwitchRow(
                checked = display.absoluteReset,
                title = stringResource(R.string.display_reset_title),
                summary = stringResource(R.string.display_reset_summary),
                onToggle = { onChange(display.copy(absoluteReset = it)) }
            )
            PrefsSwitchRow(
                checked = display.showAmounts,
                title = stringResource(R.string.display_amounts_title),
                summary = stringResource(R.string.display_amounts_summary),
                onToggle = { onChange(display.copy(showAmounts = it)) }
            )
            PrefsSwitchRow(
                checked = display.showPace,
                title = stringResource(R.string.display_pace_title),
                summary = stringResource(R.string.display_pace_summary),
                onToggle = { onChange(display.copy(showPace = it)) }
            )
            Text(stringResource(R.string.display_workdays_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.display_workdays_summary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = display.workDays == null,
                    onClick = { onChange(display.copy(workDays = null)) },
                    label = { Text(stringResource(R.string.display_workdays_auto)) }
                )
                (1..7).forEach { day ->
                    FilterChip(
                        selected = display.workDays == day,
                        onClick = { onChange(display.copy(workDays = day)) },
                        label = { Text(day.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefsSwitchRow(checked: Boolean, title: String, summary: String, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ToggleSection(
    title: String,
    description: String,
    enabled: Boolean,
    gateOnNotificationPermission: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), onResult = onToggle)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (gateOnNotificationPermission && checked && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else onToggle(checked)
                }
            )
        }
    }
}

@Composable
private fun DangerZoneSection(onDeleteAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDeleteAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Credentials")
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Credentials") },
        text = {
            Column {
                Text("This will permanently delete all saved API credentials. Type DELETE to confirm.")
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmText == "DELETE"
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
