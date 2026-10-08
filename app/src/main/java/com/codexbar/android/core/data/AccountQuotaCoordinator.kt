package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.domain.repository.StaleCredentialException
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.GeminiRepository
import com.codexbar.android.di.OpenCodeGoRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountQuota(val connection: AccountConnection, val result: Result<QuotaInfo, AppError>)

/** All foreground/background fetches for one connection share this refresh writer. */
@Singleton
class AccountQuotaCoordinator @Inject constructor(
    @ClaudeRepository claude: QuotaRepository,
    @CodexRepository codex: QuotaRepository,
    @GeminiRepository gemini: QuotaRepository,
    @OpenCodeGoRepository go: QuotaRepository,
    private val prefs: EncryptedPrefsManager,
    private val widgets: WidgetPrefsManager,
    private val notifications: QuotaNotificationService
) {
    private val repositories = mapOf(
        AiService.CLAUDE to claude, AiService.CODEX to codex,
        AiService.GEMINI to gemini, AiService.OPENCODE_GO to go
    )
    // ponytail: retain one tiny mutex per seen ID until process exit; refcount if account churn grows.
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val quotaState = MutableStateFlow<Map<String, AccountQuota>>(emptyMap())
    val quotas = quotaState.asStateFlow()

    suspend fun refresh(connection: AccountConnection): Result<QuotaInfo, AppError>? =
        locks.getOrPut(connection.id) { Mutex() }.withLock {
            val credential = prefs.loadCredential(connection) ?: return@withLock null
            val session = CredentialSession(connection, credential) { expected, updated ->
                prefs.replaceCredential(connection, expected, updated)
            }
            val result = try {
                repositories.getValue(connection.service).fetchQuota(session)
            } catch (_: StaleCredentialException) {
                currentCoroutineContext().ensureActive()
                return@withLock null
            }
            currentCoroutineContext().ensureActive()
            prefs.publishIfCurrent(connection) {
                publish(connection, result)
                result
            }
        }

    suspend fun validateAndSave(
        connection: AccountConnection,
        credential: Credential,
        previous: AccountConnection? = null
    ): Result<QuotaInfo, AppError> = locks.getOrPut(connection.id) { Mutex() }.withLock {
        if (previous != null && !prefs.isCurrent(previous)) throw StaleCredentialException()
        val draft = CredentialSession(connection, credential)
        val result = repositories.getValue(connection.service).fetchQuota(draft)
        currentCoroutineContext().ensureActive()
        if (result is Result.Success) {
            if (!prefs.saveValidatedConnection(connection, checkNotNull(draft.credential), previous)) {
                throw StaleCredentialException()
            }
            prefs.publishIfCurrent(connection) {
                if (previous != null) notifications.clearAccount(previous)
                publish(connection, result)
            }
        }
        result
    }

    fun delete(connection: AccountConnection): Boolean = synchronized(prefs) {
        if (!prefs.deleteConnection(connection)) return@synchronized false
        quotaState.update { it - connection.id }
        widgets.deleteAccountCache(connection.id)
        notifications.clearAccount(connection)
        updateNotifications()
        true
    }

    private fun publish(connection: AccountConnection, result: Result<QuotaInfo, AppError>) {
        if (result is Result.Success) widgets.cacheQuota(connection, result.value)
        quotaState.update { it + (connection.id to AccountQuota(connection, result)) }
        updateNotifications()
    }

    fun updateNotifications(): Unit = synchronized(prefs) {
        val current = prefs.loadConnections()
        if (current.isEmpty() || !prefs.isNotificationsEnabled()) {
            notifications.showQuotaNotification(emptyList())
        } else {
            notifications.showQuotaNotification(current.mapNotNull { connection ->
                val record = quotaState.value[connection.id]?.takeIf { it.connection.generation == connection.generation }
                val quota = (record?.result as? Result.Success)?.value ?: return@mapNotNull null
                connection to quota
            })
        }
    }
}
