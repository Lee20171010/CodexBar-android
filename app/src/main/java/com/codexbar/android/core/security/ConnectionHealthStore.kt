package com.codexbar.android.core.security

import android.content.Context
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionHealth {
    UNKNOWN,
    CONNECTED,
    OFFLINE,
    NEEDS_REAUTHENTICATION
}

@Singleton
class ConnectionHealthStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _health = MutableStateFlow(loadAll())
    val health: StateFlow<Map<AiService, ConnectionHealth>> = _health.asStateFlow()

    fun current(service: AiService): ConnectionHealth {
        return _health.value[service] ?: ConnectionHealth.UNKNOWN
    }

    fun record(service: AiService, result: Result<*, AppError>) {
        when (result) {
            is Result.Success -> update(service, ConnectionHealth.CONNECTED)
            is Result.Failure -> update(service, result.error.toConnectionHealth())
        }
    }

    fun update(service: AiService, value: ConnectionHealth) {
        synchronized(lock) {
            val updated = if (value == ConnectionHealth.UNKNOWN) {
                _health.value - service
            } else {
                _health.value + (service to value)
            }
            prefs.edit().apply {
                if (value == ConnectionHealth.UNKNOWN) {
                    remove(service.name)
                } else {
                    putString(service.name, value.name)
                }
            }.apply()
            _health.value = updated
        }
    }

    fun clear(service: AiService) {
        update(service, ConnectionHealth.UNKNOWN)
    }

    fun clearAll() {
        synchronized(lock) {
            prefs.edit().clear().apply()
            _health.value = emptyMap()
        }
    }

    private fun loadAll(): Map<AiService, ConnectionHealth> {
        return AiService.entries.mapNotNull { service ->
            val value = prefs.getString(service.name, null)
                ?.let { runCatching { ConnectionHealth.valueOf(it) }.getOrNull() }
                ?.takeUnless { it == ConnectionHealth.UNKNOWN }
                ?: return@mapNotNull null
            service to value
        }.toMap()
    }

    companion object {
        const val PREFS_NAME = "codexbar_connection_health"
        const val BACKUP_PATH = "$PREFS_NAME.xml"
    }
}

internal fun AppError.toConnectionHealth(): ConnectionHealth {
    return when (this) {
        is AppError.AuthError -> if (isTerminal) {
            ConnectionHealth.NEEDS_REAUTHENTICATION
        } else {
            ConnectionHealth.OFFLINE
        }
        is AppError.CredentialNotFound -> ConnectionHealth.UNKNOWN
        is AppError.NetworkError,
        is AppError.ParseError,
        is AppError.RateLimited,
        AppError.ServiceUnavailable -> ConnectionHealth.OFFLINE
    }
}
