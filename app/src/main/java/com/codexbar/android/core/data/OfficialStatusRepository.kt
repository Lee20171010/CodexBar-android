package com.codexbar.android.core.data

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.nativecli.CliProcess
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class OfficialStatus(
    val indicator: String = "unknown",
    val description: String? = null,
    val source: String? = null,
    val checkedAt: Long = 0,
    val updatedAt: String? = null
) {
    fun isRecent(now: Long = Instant.now().epochSecond) = checkedAt in (now - 1800)..now
    fun hasIncident(now: Long = Instant.now().epochSecond) = isRecent(now) && indicator in setOf("minor", "major", "critical", "maintenance")

    companion object {
        fun provider(service: AiService): String? = when (service) {
            AiService.CODEX -> "codex"
            AiService.CLAUDE -> "claude"
            AiService.COPILOT -> "copilot"
            else -> null // No official status source in the pinned Core metadata.
        }

        // null = transport/invalid-source/malformed failure; a valid "unknown" reading is non-null.
        fun parse(service: AiService, capture: CliProcess.Result?, now: Long): OfficialStatus? {
            val expectedSource = when (service) {
                AiService.CODEX -> "https://status.openai.com/"
                AiService.CLAUDE -> "https://status.claude.com/"
                AiService.COPILOT -> "https://www.githubstatus.com/"
                else -> null
            }
            if (capture == null || capture.exitCode != 0 || capture.timedOut || capture.stdoutTruncated) return null
            return try {
                val entry = Json.parseToJsonElement(capture.stdout).jsonArray.single().jsonObject
                require(entry["provider"]?.jsonPrimitive?.content == provider(service))
                val status = entry["status"]?.jsonObject ?: return null
                require(expectedSource != null && status["url"]?.jsonPrimitive?.content == expectedSource)
                val indicator = status["indicator"]?.jsonPrimitive?.content
                require(indicator in setOf("none", "minor", "major", "critical", "maintenance", "unknown"))
                OfficialStatus(source = expectedSource, checkedAt = now).copy(indicator = indicator!!,
                    description = if (indicator == "unknown") null else status["description"]?.jsonPrimitive?.contentOrNull?.take(300),
                    updatedAt = status["updatedAt"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toString() })
            } catch (_: Exception) { null }
        }
    }
}

@Singleton
class OfficialStatusRepository internal constructor(
    private val prefs: SharedPreferences,
    private val fetch: suspend (String) -> CliProcess.Result?,
    private val clock: () -> Long = { Instant.now().epochSecond },
    private val enabled: () -> Boolean = { true }
) {
    @Inject constructor(@ApplicationContext context: Context, native: NativeCodexBarClient, prefsManager: com.codexbar.android.core.security.EncryptedPrefsManager) :
        this(context.getSharedPreferences("official_status", Context.MODE_PRIVATE), native::fetchStatus, enabled = prefsManager::isStatusChecksEnabled)

    private val mutex = Mutex()
    private val state = MutableStateFlow(AiService.entries.associateWith { service ->
        try { Json.decodeFromString<OfficialStatus>(prefs.getString(service.name, null) ?: "{}") }
        catch (_: Exception) { OfficialStatus() }
    })
    val statuses = state.asStateFlow()

    suspend fun refresh(service: AiService) = mutex.withLock {
        if (!enabled()) return@withLock
        val now = clock()
        if (state.value[service]?.checkedAt?.let { it > 0 && now - it in 0 until 300 } == true) return@withLock
        val value = try { OfficialStatus.parse(service, OfficialStatus.provider(service)?.let { fetch(it) }, now) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        // Mirrors Mac statusRefreshPublicationIsCurrent: a disable during the fetch drops the result.
        if (!enabled()) return@withLock
        // A failed check is no new information: keep the last recorded status with its original
        // checkedAt (Mac preserves last good on fetch error). First failure records an unknown
        // attempt so the throttle and age line still work. A valid unknown publishes normally.
        val next = value ?: state.value[service]?.takeIf { it.checkedAt > 0 } ?: OfficialStatus(checkedAt = now)
        runCatching { prefs.edit().putString(service.name, Json.encodeToString(next)).apply() }
        state.update { it + (service to next) }
    }
}
