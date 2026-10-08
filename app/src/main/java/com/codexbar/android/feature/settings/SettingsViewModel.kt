package com.codexbar.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.StaleCredentialException
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.updateQuotaSurfaces
import com.codexbar.android.core.network.codex.CodexDeviceAuth
import com.codexbar.android.core.network.copilot.CopilotDeviceAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accounts: AccountQuotaCoordinator,
    private val prefsManager: EncryptedPrefsManager,
    private val codexDeviceAuth: CodexDeviceAuth,
    private val copilotDeviceAuth: CopilotDeviceAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(
        connections = prefsManager.loadConnections(),
        refreshIntervalMinutes = prefsManager.getRefreshInterval(),
        notificationsEnabled = prefsManager.isNotificationsEnabled(),
        recoveryAlertsEnabled = prefsManager.isRecoveryAlertsEnabled()
    ))
    val uiState = _uiState.asStateFlow()
    private val validationJobs = mutableMapOf<AiService, Job>()

    init {
        viewModelScope.launch {
            prefsManager.connections.collect { connections ->
                _uiState.update { it.copy(connections = connections) }
            }
        }
    }

    fun beginAdd(service: AiService) {
        cancelDraft(service)
        setService(service, ServiceCredentialState(connection = AccountConnection.create(service), name = service.displayName))
    }

    fun beginReconnect(connection: AccountConnection) {
        cancelDraft(connection.service)
        setService(connection.service, ServiceCredentialState(
            connection = connection.reconnect(), previous = connection, name = connection.name
        ))
    }

    fun cancelDraft(service: AiService) {
        validationJobs.remove(service)?.cancel()
        setService(service, ServiceCredentialState())
    }

    fun updateField(service: AiService, field: String, value: String) {
        validationJobs.remove(service)?.cancel()
        val state = _uiState.value.serviceStates.getValue(service)
        val updated = when (field) {
            "name" -> state.copy(name = value)
            "accessToken" -> state.copy(accessToken = value)
            "refreshToken" -> state.copy(refreshToken = value)
            "accountId" -> state.copy(accountId = value)
            "oauthClientId" -> state.copy(oauthClientId = value)
            "oauthClientSecret" -> state.copy(oauthClientSecret = value)
            else -> state
        }
        setService(service, updated.copy(isValidating = false, deviceUserCode = null, validationResult = null))
    }

    fun signIn(service: AiService) {
        require(service == AiService.CODEX || service == AiService.COPILOT)
        validationJobs.remove(service)?.cancel()
        val state = _uiState.value.serviceStates.getValue(service)
        val draft = state.connection ?: return
        val connection = try { draft.copy(name = state.name.trim()) } catch (_: IllegalArgumentException) {
            setService(service, state.copy(validationResult = ValidationResult.Failure("Enter a valid account name.")))
            return
        }
        setService(service, state.copy(isValidating = true, deviceUserCode = null, validationResult = null))
        validationJobs[service] = viewModelScope.launch {
            try {
                val credential = if (service == AiService.CODEX) {
                    val challenge = codexDeviceAuth.requestCode()
                    setService(service, state.copy(isValidating = true, deviceUserCode = challenge.userCode, validationResult = null))
                    codexDeviceAuth.awaitCredential(challenge)
                } else {
                    val challenge = copilotDeviceAuth.requestCode()
                    setService(service, state.copy(isValidating = true, deviceUserCode = challenge.userCode, validationResult = null))
                    copilotDeviceAuth.awaitCredential(challenge)
                }
                // Tokens never enter editable UI state. The existing account owner validates and
                // atomically publishes them, or preserves the previous account on reconnect failure.
                when (val result = accounts.validateAndSave(connection, credential, state.previous)) {
                    is Result.Success -> {
                        setService(service, ServiceCredentialState())
                        updateQuotaSurfaces(context)
                    }
                    is Result.Failure -> setService(service, state.copy(
                        deviceUserCode = null, validationResult = ValidationResult.Failure(formatAppError(result.error))))
                }
            } catch (_: StaleCredentialException) {
                currentCoroutineContext().ensureActive()
                setService(service, state.copy(deviceUserCode = null,
                    validationResult = ValidationResult.Failure("Account changed. Start reconnect again.")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                setService(service, state.copy(deviceUserCode = null, validationResult = ValidationResult.Failure(
                    "Sign-in failed or expired. Check device-code access and connectivity, then try again.")))
            }
        }
    }

    fun validateCredential(service: AiService) {
        validationJobs.remove(service)?.cancel()
        val state = _uiState.value.serviceStates.getValue(service)
        val draft = state.connection ?: return
        val connection: AccountConnection
        val credential: Credential
        try {
            connection = draft.copy(name = state.name.trim())
            require(state.accessToken.isNotBlank())
            credential = when (service) {
                AiService.OPENCODE_GO -> Credential.OpenCodeGoCredential(state.accessToken.trim())
                AiService.OPENROUTER -> Credential.OpenRouterCredential(state.accessToken.trim())
                AiService.COPILOT -> Credential.CopilotCredential(state.accessToken.trim())
                AiService.DEEPSEEK -> Credential.DeepSeekCredential(state.accessToken.trim())
                AiService.CLAUDE -> Credential.ClaudeCredential(
                    state.accessToken.trim(), state.refreshToken.trim().ifBlank { null }
                )
                AiService.CODEX -> {
                    require(state.refreshToken.isNotBlank())
                    Credential.CodexCredential(state.accessToken.trim(), state.refreshToken.trim(), state.accountId.trim().ifBlank { null })
                }
                AiService.GEMINI -> {
                    require(state.refreshToken.isNotBlank() && state.oauthClientId.isNotBlank() && state.oauthClientSecret.isNotBlank())
                    // Unknown expiry: request a real refresh instead of inventing token freshness.
                    Credential.GeminiCredential(state.accessToken.trim(), state.refreshToken.trim(), 0L,
                        state.oauthClientId.trim(), state.oauthClientSecret.trim())
                }
            }
        } catch (_: IllegalArgumentException) {
            setService(service, state.copy(validationResult = ValidationResult.Failure("Enter a valid account name and required credentials.")))
            return
        }
        setService(service, state.copy(isValidating = true, validationResult = null))
        validationJobs[service] = viewModelScope.launch {
            try {
                when (val result = accounts.validateAndSave(connection, credential, state.previous)) {
                    is Result.Success -> {
                        setService(service, ServiceCredentialState())
                        updateQuotaSurfaces(context)
                    }
                    is Result.Failure -> setService(service, state.copy(validationResult = ValidationResult.Failure(formatAppError(result.error))))
                }
            } catch (_: StaleCredentialException) {
                currentCoroutineContext().ensureActive()
                setService(service, state.copy(validationResult = ValidationResult.Failure("Account changed. Start reconnect again.")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                setService(service, state.copy(validationResult = ValidationResult.Failure("Could not save account. Restart and try again.")))
            }
        }
    }

    fun rename(connection: AccountConnection, name: String) {
        prefsManager.renameConnection(connection, name)
        accounts.updateNotifications()
        viewModelScope.launch { updateQuotaSurfaces(context) }
    }

    fun delete(connection: AccountConnection) {
        if (_uiState.value.serviceStates[connection.service]?.previous?.id == connection.id) cancelDraft(connection.service)
        accounts.delete(connection)
        viewModelScope.launch { updateQuotaSurfaces(context) }
    }

    fun setRefreshInterval(minutes: Long) {
        prefsManager.setRefreshInterval(minutes)
        com.codexbar.android.core.workmanager.WorkManagerInitializer.schedulePeriodicRefresh(context, minutes)
        _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefsManager.setNotificationsEnabled(enabled)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        accounts.updateNotifications()
    }

    fun setRecoveryAlertsEnabled(enabled: Boolean) {
        prefsManager.setRecoveryAlertsEnabled(enabled)
        _uiState.update { it.copy(recoveryAlertsEnabled = enabled) }
    }

    fun showDeleteConfirmDialog() { _uiState.update { it.copy(showDeleteConfirmDialog = true) } }
    fun dismissDeleteConfirmDialog() { _uiState.update { it.copy(showDeleteConfirmDialog = false) } }

    fun deleteAllCredentials() {
        AiService.entries.forEach(::cancelDraft)
        prefsManager.loadConnections().forEach(accounts::delete)
        viewModelScope.launch { updateQuotaSurfaces(context) }
        dismissDeleteConfirmDialog()
    }

    private fun setService(service: AiService, state: ServiceCredentialState) {
        _uiState.update { it.copy(serviceStates = it.serviceStates + (service to state)) }
    }

    private fun formatAppError(error: AppError): String = when (error) {
        is AppError.NetworkError -> "Network error. Check connectivity and try again."
        is AppError.AuthError -> "Credentials rejected. Check this account's credentials."
        is AppError.RateLimited -> "Rate limited — try again later"
        is AppError.ParseError -> "Unexpected provider response"
        is AppError.CredentialNotFound -> "Required credentials missing"
        is AppError.ServiceUnavailable -> "Service temporarily unavailable"
    }
}
