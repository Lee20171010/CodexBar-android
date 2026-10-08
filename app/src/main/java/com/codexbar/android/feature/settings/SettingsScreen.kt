package com.codexbar.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.BuildConfig
import com.codexbar.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }
    var renameAccount by remember { mutableStateOf<AccountConnection?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteAccount by remember { mutableStateOf<AccountConnection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notifications
            NotificationsSection(
                enabled = uiState.notificationsEnabled,
                onToggle = { viewModel.setNotificationsEnabled(it) }
            )

            Text("Accounts", style = MaterialTheme.typography.titleLarge)
            uiState.connections.forEach { account ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium)
                        Text(account.service.displayName, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { renameAccount = account; renameText = account.name }) { Text("Rename") }
                            TextButton(onClick = { viewModel.beginReconnect(account) }) { Text("Reconnect") }
                            TextButton(onClick = { deleteAccount = account }) { Text("Delete") }
                        }
                    }
                }
            }

            AiService.entries.filter { !it.usesNativeApiKey || BuildConfig.NATIVE_CLI_ENABLED }.forEach { service ->
                val state = uiState.serviceStates[service] ?: ServiceCredentialState()
                if (state.connection == null) OutlinedButton(
                    onClick = { viewModel.beginAdd(service) }, modifier = Modifier.fillMaxWidth()
                ) { Text("Add ${service.displayName} account") }
                else ServiceCredentialSection(
                    service = service,
                    state = state,
                    onFieldChange = { field, value -> viewModel.updateField(service, field, value) },
                    onValidate = { viewModel.validateCredential(service) },
                    onCancel = { viewModel.cancelDraft(service) }
                )
            }

            // Refresh interval
            RefreshIntervalSection(
                currentMinutes = uiState.refreshIntervalMinutes,
                onIntervalChange = { viewModel.setRefreshInterval(it) }
            )

            // Danger zone
            DangerZoneSection(
                onDeleteAll = { viewModel.showDeleteConfirmDialog() }
            )

            TextButton(onClick = { showAbout = true }) {
                Text(stringResource(R.string.about_licenses))
            }
            Spacer(modifier = Modifier.height(32.dp))
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
private fun ServiceCredentialSection(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onValidate: () -> Unit,
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

            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { onFieldChange("accessToken", it) },
                label = { Text(if (service.usesNativeApiKey) "${service.displayName} API Key" else "Access Token") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (service.usesNativeApiKey) {
                    { Text("Paste your ${service.displayName} API key. Stored encrypted; data is fetched by the native Core/CLI.") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!service.usesNativeApiKey) OutlinedTextField(
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
                text = "Refresh Interval",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            val options = listOf(15L to "15 min", 30L to "30 min", 60L to "1 hour", 0L to "Manual")
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

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
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
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Quota status and reset alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
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
