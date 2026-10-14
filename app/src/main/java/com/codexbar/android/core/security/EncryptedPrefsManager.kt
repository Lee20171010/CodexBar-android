package com.codexbar.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsManager internal constructor(
    private val prefs: SharedPreferences
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        EncryptedSharedPreferences.create(
            "codexbar_secure_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    )

    private val connectionState = MutableStateFlow<List<AccountConnection>>(emptyList())
    val connections = connectionState.asStateFlow()
    private var writeFailed = false

    // Mac multiAccountMenuLayout (default .segmented, SettingsStore.swift:139-153). Resolved once at
    // storage init and persisted before any account can be created; the existing-account check is only an
    // upgrade heuristic for the missing key, never re-derived afterwards, so an explicit choice and a
    // fresh-install default both survive adding accounts, restart, and delete-all.
    private val multiAccountSegmentedState = MutableStateFlow(
        if (prefs.contains("multi_account_segmented")) prefs.getBoolean("multi_account_segmented", true)
        else {
            val resolved = !prefs.all.keys.any {
                it.endsWith("_connection_provider") || it.endsWith("_access_token")
            }
            commit(prefs.edit().putBoolean("multi_account_segmented", resolved))
            resolved
        }
    )
    val multiAccountSegmented = multiAccountSegmentedState.asStateFlow()

    fun isMultiAccountSegmented(): Boolean = multiAccountSegmentedState.value

    fun setMultiAccountSegmented(enabled: Boolean) {
        commit(prefs.edit().putBoolean("multi_account_segmented", enabled))
        multiAccountSegmentedState.value = enabled
    }

    // Display-only pointer per service; unlike Mac's Codex selection it never changes credential
    // ownership, and widgets keep their own per-widget pins and never read this.
    private val displayedState = MutableStateFlow(
        runCatching {
            json.decodeFromString(displayedMapSerializer, prefs.getString("displayed_connections", "{}") ?: "{}")
        }.getOrDefault(emptyMap())
    )
    val displayedConnections = displayedState.asStateFlow()

    /**
     * Stores the displayed connection for its service; returns false when the selection is no
     * longer current. Validation, commit, and flow publication share the instance monitor that all
     * account mutations use, so a concurrent delete/reconnect cannot slip between them.
     */
    @Synchronized
    fun setDisplayedConnection(connection: AccountConnection): Boolean {
        if (!isCurrent(connection)) return false
        val next = displayedState.value + (connection.service.name to connection.id)
        commit(prefs.edit().putString("displayed_connections", json.encodeToString(displayedMapSerializer, next)))
        displayedState.value = next
        return true
    }

    private fun writeCredential(
        editor: SharedPreferences.Editor,
        prefix: String,
        service: AiService,
        credential: Credential
    ) {
        val owner = when (credential) {
            is Credential.ClaudeCredential -> AiService.CLAUDE
            is Credential.CodexCredential -> AiService.CODEX
            is Credential.GeminiCredential -> AiService.GEMINI
            is Credential.OpenCodeGoCredential -> AiService.OPENCODE_GO
            is Credential.OpenRouterCredential -> AiService.OPENROUTER
            is Credential.CopilotCredential -> AiService.COPILOT
            is Credential.DeepSeekCredential -> AiService.DEEPSEEK
            is Credential.VeniceCredential -> AiService.VENICE
            is Credential.AmpCredential -> AiService.AMP
            is Credential.ZaiCredential -> AiService.ZAI
            is Credential.FactoryCredential -> AiService.FACTORY
            is Credential.AlibabaCodingPlanCredential -> AiService.ALIBABA_CODING_PLAN
            is Credential.HyperCredential -> AiService.HYPER
            is Credential.KimiCredential -> AiService.KIMI
            is Credential.MiniMaxCredential -> AiService.MINIMAX
        }
        require(owner == service) { "Credential provider mismatch" }
        require(credential.accessToken.isNotBlank()) { "Access token is required" }
        CREDENTIAL_FIELDS.forEach { editor.remove("${prefix}_$it") }

        editor.putString("${prefix}_access_token", credential.accessToken)
        editor.putString("${prefix}_refresh_token", credential.refreshToken)

        when (credential) {
            is Credential.ClaudeCredential -> {
                credential.expiresAt?.let {
                    editor.putLong("${prefix}_expires_at", it.epochSecond)
                }
                credential.scopes?.let {
                    editor.putString("${prefix}_scopes", it)
                }
                credential.rateLimitTier?.let {
                    editor.putString("${prefix}_rate_limit_tier", it)
                }
            }
            is Credential.CodexCredential -> {
                credential.lastRefresh?.let { editor.putString("${prefix}_last_refresh", it.toString()) }
                credential.accountId?.let {
                    editor.putString("${prefix}_account_id", it)
                }
            }
            is Credential.GeminiCredential -> {
                editor.putLong("${prefix}_expires_at_ms", credential.expiresAtMs)
                editor.putString("${prefix}_oauth_client_id", credential.oauthClientId)
                editor.putString("${prefix}_oauth_client_secret", credential.oauthClientSecret)
            }
            is Credential.OpenCodeGoCredential -> Unit
            is Credential.OpenRouterCredential -> Unit
            is Credential.CopilotCredential -> Unit
            is Credential.DeepSeekCredential -> Unit
            is Credential.VeniceCredential -> Unit
            is Credential.AmpCredential -> Unit
            is Credential.ZaiCredential -> Unit
            is Credential.FactoryCredential -> Unit
            is Credential.AlibabaCodingPlanCredential -> Unit
            is Credential.HyperCredential -> Unit
            is Credential.KimiCredential -> Unit
            is Credential.MiniMaxCredential -> Unit
        }

    }

    @Synchronized
    fun loadCredential(service: AiService): Credential? {
        checkReadable()
        return readCredential(service, service.name)
    }

    private fun readCredential(service: AiService, prefix: String): Credential? {
        val accessToken = prefs.getString("${prefix}_access_token", null) ?: return null

        return when (service) {
            AiService.OPENCODE_GO -> Credential.OpenCodeGoCredential(accessToken)
            AiService.OPENROUTER -> Credential.OpenRouterCredential(accessToken)
            AiService.COPILOT -> Credential.CopilotCredential(accessToken)
            AiService.DEEPSEEK -> Credential.DeepSeekCredential(accessToken)
            AiService.VENICE -> Credential.VeniceCredential(accessToken)
            AiService.AMP -> Credential.AmpCredential(accessToken)
            AiService.ZAI -> Credential.ZaiCredential(accessToken)
            AiService.FACTORY -> Credential.FactoryCredential(accessToken)
            AiService.ALIBABA_CODING_PLAN -> Credential.AlibabaCodingPlanCredential(accessToken)
            AiService.HYPER -> Credential.HyperCredential(accessToken)
            AiService.KIMI -> Credential.KimiCredential(accessToken)
            AiService.MINIMAX -> Credential.MiniMaxCredential(accessToken)
            AiService.CLAUDE -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null)
                val expiresAt = prefs.getLong("${prefix}_expires_at", -1L)
                    .takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it) }
                val scopes = prefs.getString("${prefix}_scopes", null)
                val rateLimitTier = prefs.getString("${prefix}_rate_limit_tier", null)
                Credential.ClaudeCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    scopes = scopes,
                    rateLimitTier = rateLimitTier
                )
            }
            AiService.CODEX -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return null
                val accountId = prefs.getString("${prefix}_account_id", null)
                Credential.CodexCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId,
                    lastRefresh = prefs.getString("${prefix}_last_refresh", null)?.let(Instant::parse)
                )
            }
            AiService.GEMINI -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return null
                val expiresAtMs = prefs.getLong("${prefix}_expires_at_ms", -1L)
                    .takeIf { it > 0 } ?: return null
                val clientId = prefs.getString("${prefix}_oauth_client_id", null) ?: return null
                val clientSecret = prefs.getString("${prefix}_oauth_client_secret", null) ?: return null
                Credential.GeminiCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = expiresAtMs,
                    oauthClientId = clientId,
                    oauthClientSecret = clientSecret
                )
            }
        }
    }

    @Synchronized
    fun deleteAllCredentials() {
        val prefixes = (loadConnections().map { it.id } + AiService.entries.map { it.name })
            .map { "${it}_" }
        val editor = prefs.edit()
        prefs.all.keys.filter { key -> prefixes.any(key::startsWith) }.forEach(editor::remove)
        commit(editor)
        loadConnections()
    }

    fun getRefreshInterval(): Long {
        return prefs.getLong("refresh_interval_minutes", 30L)
    }

    fun setRefreshInterval(minutes: Long) {
        prefs.edit().putLong("refresh_interval_minutes", minutes).apply()
    }

    fun getRefreshOnOpen(): Boolean = prefs.getBoolean("refresh_on_open", false)

    fun setRefreshOnOpen(enabled: Boolean) {
        prefs.edit().putBoolean("refresh_on_open", enabled).apply()
    }

    /** True once the one-time battery-exemption offer has been shown. */
    fun isBatteryOfferShown(): Boolean = prefs.getBoolean("battery_offer_shown", false)

    fun setBatteryOfferShown(shown: Boolean) {
        prefs.edit().putBoolean("battery_offer_shown", shown).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    private val statusChecksState = MutableStateFlow(prefs.getBoolean("status_checks_enabled", true))
    val statusChecksEnabled = statusChecksState.asStateFlow()

    fun isStatusChecksEnabled(): Boolean = statusChecksState.value

    fun setStatusChecksEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("status_checks_enabled", enabled).apply()
        statusChecksState.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun isRecoveryAlertsEnabled(): Boolean = prefs.getBoolean("recovery_alerts_enabled", false)

    fun setRecoveryAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("recovery_alerts_enabled", enabled).apply()
    }

    fun isAlertDepletedEnabled(): Boolean = prefs.getBoolean("alert_depleted_enabled", false)

    fun setAlertDepletedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("alert_depleted_enabled", enabled).apply()
    }

    fun isAlertLowEnabled(): Boolean = prefs.getBoolean("alert_low_enabled", false)

    fun setAlertLowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("alert_low_enabled", enabled).apply()
    }

    fun isAlertPaceEnabled(): Boolean = prefs.getBoolean("alert_pace_enabled", false)

    /** Mac parity: disabling clears pace receipts immediately, so OFF→ON rearms without a refresh. */
    @Synchronized
    fun setAlertPaceEnabled(enabled: Boolean) {
        val edit = prefs.edit().putBoolean("alert_pace_enabled", enabled)
        if (!enabled) {
            for (key in prefs.all.keys.filter { it.endsWith("_alerts_v1") }) {
                val json = prefs.getString(key, null) ?: continue
                val previous = runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<Map<String, com.codexbar.android.core.notification.AlertReading>>(json)
                }.getOrNull() ?: continue
                val next = previous.mapValues { (_, reading) ->
                    if (reading.paceNotifiedAt > 0 || reading.paceCycleResetsAt > 0 ||
                        reading.paceCycleDuration != null
                    ) reading.copy(paceNotifiedAt = 0, paceCycleResetsAt = 0, paceCycleDuration = null)
                    else reading
                }
                if (next != previous) edit.putString(key, kotlinx.serialization.json.Json.encodeToString(next))
            }
        }
        commit(edit)
    }

    /** Persist receipts before notification: restart cannot repeat an already claimed alert. */
    @Synchronized
    fun recordAlerts(connection: AccountConnection, quota: com.codexbar.android.core.domain.model.QuotaInfo,
        workDays: Int? = null):
        List<Pair<com.codexbar.android.core.domain.model.UsageWindow, com.codexbar.android.core.notification.QuotaAlertKind>> {
        if (!isCurrent(connection) || quota.service != connection.service) return emptyList()
        val settings = com.codexbar.android.core.notification.AlertSettings(
            depleted = isAlertDepletedEnabled(), low = isAlertLowEnabled(), pace = isAlertPaceEnabled()
        )
        if (!settings.depleted && !settings.low && !settings.pace) return emptyList()
        val key = "${connection.id}_alerts_v1"
        val previous = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, com.codexbar.android.core.notification.AlertReading>>(
                prefs.getString(key, null) ?: "{}")
        }.getOrDefault(emptyMap())
        val (next, alerts) = com.codexbar.android.core.notification.QuotaAlerts
            .observe(previous, quota, Instant.now().epochSecond, settings, workDays)
        commit(prefs.edit().putString(key, kotlinx.serialization.json.Json.encodeToString(next)))
        return alerts
    }

    /** Persist receipt before notification: restart cannot repeat an already claimed recovery. */
    @Synchronized
    fun recordRecovery(connection: AccountConnection, quota: com.codexbar.android.core.domain.model.QuotaInfo):
        List<com.codexbar.android.core.domain.model.UsageWindow> {
        if (!isCurrent(connection) || quota.service != connection.service) return emptyList()
        val key = "${connection.id}_recovery_v1"
        val previous = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, com.codexbar.android.core.notification.RecoveryReading>>(
                prefs.getString(key, null) ?: "{}")
        }.getOrDefault(emptyMap())
        val enabled = isRecoveryAlertsEnabled()
        val (next, recovered) = com.codexbar.android.core.notification.QuotaRecovery.observe(previous, quota, Instant.now().epochSecond, enabled)
        commit(prefs.edit().putString(key, kotlinx.serialization.json.Json.encodeToString(next)))
        return recovered
    }

    @Synchronized
    fun saveResetTimes(connection: AccountConnection, windows: List<Pair<String, Instant?>>): Boolean {
        if (!isCurrent(connection)) return false
        writeResetTimes(connection.id, windows)
        return true
    }

    private fun writeResetTimes(prefix: String, windows: List<Pair<String, Instant?>>) {
        val editor = prefs.edit()
        windows.forEach { (label, resetsAt) ->
            val key = "${prefix}_${label}_resets_at"
            if (resetsAt != null) {
                editor.putLong(key, resetsAt.epochSecond)
            } else {
                editor.remove(key)
            }
        }
        commit(editor)
    }

    @Synchronized
    fun loadResetTimes(connection: AccountConnection): Map<String, Instant> =
        if (isCurrent(connection)) readResetTimes(connection.id) else emptyMap()

    private fun readResetTimes(id: String): Map<String, Instant> {
        val prefix = "${id}_"
        val suffix = "_resets_at"
        return prefs.all
            .filter { it.key.startsWith(prefix) && it.key.endsWith(suffix) }
            .mapNotNull { (key, value) ->
                val label = key.removePrefix(prefix).removeSuffix(suffix)
                val epochSecond = (value as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                label to Instant.ofEpochSecond(epochSecond)
            }
            .toMap()
    }

    /** Adopts existing namespaces in place. No credential or global setting is removed. */
    @Synchronized
    fun loadConnections(): List<AccountConnection> {
        checkReadable()
        val editor = prefs.edit()
        var migrated = false
        AiService.entries.forEach { service ->
            val prefix = service.name
            if (prefs.contains("${prefix}_access_token") &&
                !prefs.contains("${prefix}_connection_generation")) {
                editor.putString("${prefix}_connection_provider", service.name)
                editor.putString("${prefix}_connection_generation", UUID.randomUUID().toString())
                migrated = true
            }
        }
        if (migrated) commit(editor)
        val result = prefs.all.keys.filter { it.endsWith("_connection_provider") }.map { key ->
            val id = key.removeSuffix("_connection_provider")
            val service = AiService.valueOf(requireNotNull(prefs.getString(key, null)))
            AccountConnection(
                id, service,
                prefs.getString("${id}_connection_name", null) ?: service.displayName,
                requireNotNull(prefs.getString("${id}_connection_generation", null))
            )
        }.sortedWith(compareBy(
            { prefs.getLong("${it.id}_connection_created", it.service.ordinal.toLong()) },
            AccountConnection::id
        ))
        connectionState.value = result
        return result
    }

    @Synchronized
    fun isCurrent(connection: AccountConnection): Boolean = loadConnections().any {
        it.id == connection.id && it.service == connection.service && it.generation == connection.generation
    }

    /** The caller must validate a private draft before committing. Reconnect is compare-and-set. */
    @Synchronized
    fun saveValidatedConnection(
        connection: AccountConnection,
        credential: Credential,
        previous: AccountConnection? = null
    ): Boolean {
        val current = loadConnections()
        if (previous == null) {
            require(connection.id != connection.service.name) { "New accounts require a unique ID" }
            if (current.any { it.id == connection.id }) return false
        } else {
            require(previous.id == connection.id && previous.service == connection.service)
            require(previous.generation != connection.generation) { "Reconnect requires a new generation" }
            if (!isCurrent(previous)) return false
        }
        val editor = prefs.edit()
        if (previous != null) removeConnectionEntries(editor, connection.id)
        writeCredential(editor, connection.id, connection.service, credential)
        editor.putString("${connection.id}_connection_provider", connection.service.name)
        editor.putString("${connection.id}_connection_name", connection.name)
        editor.putString("${connection.id}_connection_generation", connection.generation)
        editor.putLong("${connection.id}_connection_created",
            prefs.getLong("${connection.id}_connection_created", System.currentTimeMillis()))
        commit(editor)
        loadConnections()
        return true
    }

    @Synchronized
    fun loadCredential(connection: AccountConnection): Credential? =
        if (isCurrent(connection)) readCredential(connection.service, connection.id) else null

    /** Token rotation keeps identity/generation and cannot resurrect a deleted or reconnected account. */
    @Synchronized
    fun replaceCredential(connection: AccountConnection, expected: Credential, replacement: Credential): Boolean {
        val current = loadCredential(connection) ?: return false
        val same = current == expected || (current is Credential.OpenCodeGoCredential &&
            expected is Credential.OpenCodeGoCredential && current.accessToken == expected.accessToken)
        if (!same) return false
        val editor = prefs.edit()
        writeCredential(editor, connection.id, connection.service, replacement)
        commit(editor)
        return true
    }

    @Synchronized
    fun renameConnection(connection: AccountConnection, name: String): Boolean {
        val renamed = connection.copy(name = name.trim())
        if (!isCurrent(connection)) return false
        commit(prefs.edit().putString("${connection.id}_connection_name", renamed.name))
        loadConnections()
        return true
    }

    @Synchronized
    fun deleteConnection(connection: AccountConnection): Boolean {
        if (!isCurrent(connection)) return false
        val editor = prefs.edit()
        removeConnectionEntries(editor, connection.id)
        commit(editor)
        loadConnections()
        return true
    }

    /** Small synchronous publication only; never perform network or suspend work under this lock. */
    @Synchronized
    fun <T> publishIfCurrent(connection: AccountConnection, publish: () -> T): T? =
        if (isCurrent(connection)) publish() else null

    private fun removeConnectionEntries(editor: SharedPreferences.Editor, id: String) {
        prefs.all.keys.filter { it.startsWith("${id}_") }.forEach(editor::remove)
    }

    private fun checkReadable() {
        if (writeFailed) throw IOException("Account storage unavailable until restart")
    }

    private fun commit(editor: SharedPreferences.Editor) {
        checkReadable()
        if (!editor.commit()) {
            // SharedPreferences may update memory even when its disk write fails. Fail closed.
            writeFailed = true
            throw IOException("Unable to persist account data")
        }
    }

    private companion object {
        val json = kotlinx.serialization.json.Json
        val displayedMapSerializer = MapSerializer(String.serializer(), String.serializer())
        val CREDENTIAL_FIELDS = listOf(
            "access_token", "refresh_token", "expires_at", "scopes", "rate_limit_tier",
            "account_id", "last_refresh", "expires_at_ms", "oauth_client_id", "oauth_client_secret"
        )
    }
}
