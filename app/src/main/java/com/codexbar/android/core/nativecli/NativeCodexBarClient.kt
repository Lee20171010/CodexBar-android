package com.codexbar.android.core.nativecli

import android.content.Context
import android.os.Build
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.Credential
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class NativeCodexBarClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    suspend fun fetchOpenCodeGo(apiKey: String): Result<QuotaInfo, AppError> = fetchApiKey(AiService.OPENCODE_GO, apiKey)

    suspend fun fetchCodex(credential: Credential.CodexCredential): Result<QuotaInfo, AppError> =
        fetch(AiService.CODEX, credential.accessToken, credential)

    suspend fun fetchApiKey(service: AiService, apiKey: String): Result<QuotaInfo, AppError> = fetch(service, apiKey)

    suspend fun fetchStatus(provider: String): CliProcess.Result? = mutex.withLock {
        require(provider in setOf("codex", "claude", "copilot"))
        if (Build.VERSION.SDK_INT < 28) return@withLock null
        try { run(provider, statusOnly = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private suspend fun fetch(service: AiService, apiKey: String, codex: Credential.CodexCredential? = null): Result<QuotaInfo, AppError> = mutex.withLock {
        val (provider, keyVariable) = when (service) {
            AiService.OPENCODE_GO -> "opencodego" to "OPENCODE_API_KEY"
            AiService.OPENROUTER -> "openrouter" to "OPENROUTER_API_KEY"
            AiService.COPILOT -> "copilot" to "COPILOT_API_TOKEN"
            AiService.CODEX -> if (codex != null) "codex" to "" else
                return@withLock Result.Failure(AppError.ParseError("Codex requires an OAuth credential session."))
            else -> return@withLock Result.Failure(AppError.ParseError("Unsupported native API-key provider."))
        }
        if (apiKey.isBlank() || apiKey.any { it.isWhitespace() || it.isISOControl() }) {
            return@withLock Result.Failure(AppError.ParseError("Enter an API key without whitespace."))
        }
        if (codex != null && CodexCredentialBridge.needsRefresh(codex)) {
            return@withLock Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = false))
        }
        if (Build.VERSION.SDK_INT < 28) {
            return@withLock Result.Failure(AppError.ParseError("The native engine requires Android 9 or newer."))
        }
        val binary = File(context.applicationInfo.nativeLibraryDir, "libcodexbar.so")
        if (!binary.canExecute()) {
            return@withLock Result.Failure(AppError.ParseError("Install the native-engine build to use ${service.displayName}."))
        }
        try {
            val capture = run(provider, keyVariable, apiKey, codex)
            if (service == AiService.OPENROUTER) OpenRouterCliParser.parse(capture)
            else NativeQuotaCliParser.parse(capture, service)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never expose raw subprocess output or exceptions containing request/credential details.
            Result.Failure(AppError.NetworkError("${service.displayName} query failed. Check your connection and try again."))
        }
    }

    private suspend fun run(provider: String, keyVariable: String = "", apiKey: String = "",
                            codex: Credential.CodexCredential? = null, statusOnly: Boolean = false): CliProcess.Result =
        runInterruptible(Dispatchers.IO) {
                val binary = File(context.applicationInfo.nativeLibraryDir, "libcodexbar.so")
                check(binary.canExecute())
                // Per-request private workspace avoids stale assets/provider state after app updates.
                // The API key exists only in memory and the child's environment, never in these files.
                CodexCredentialBridge.clearAbandonedHomes(context.noBackupFilesDir)
                val home = Files.createTempDirectory(context.noBackupFilesDir.toPath(), CodexCredentialBridge.HOME_PREFIX).toFile()
                try {
                    copyAssets("codexbar", home)
                    if (codex != null) CodexCredentialBridge.write(home, codex)
                    val config = File(home, "config.json").apply {
                        writeText("""{"version":1,"providers":[{"id":"$provider","enabled":true}]}""")
                    }
                    val env = mutableMapOf<String, String>()
                    for (name in listOf("ANDROID_ROOT", "ANDROID_DATA", "ANDROID_ART_ROOT", "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT")) {
                        System.getenv(name)?.let { env[name] = it }
                    }
                    env.putAll(mapOf(
                        "HOME" to home.path,
                        "CODEX_HOME" to File(home, ".codex").path,
                        "CLAUDE_CONFIG_DIR" to File(home, ".claude").path,
                        "XDG_CONFIG_HOME" to File(home, ".config").path,
                        "XDG_DATA_HOME" to File(home, ".local/share").path,
                        "XDG_CACHE_HOME" to File(home, ".cache").path,
                        "TMPDIR" to home.path,
                        "PATH" to "/system/bin",
                        "LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir,
                        "CODEXBAR_CONFIG" to config.path,
                        "CODEXBAR_RESOURCE_BUNDLE_PATH" to File(home, "CodexBar_CodexBarCore.bundle").path,
                    ))
                    if (!statusOnly && codex == null) env[keyVariable] = apiKey
                    val arguments = if (statusOnly) listOf("usage", "--provider", provider, "--status-only", "--json")
                        else listOf("usage", "--provider", provider, "--source", if (codex == null) "api" else "oauth", "--json")
                    CliProcess.run(binary, arguments,
                        env, home, 45_000)
                } finally {
                    home.deleteRecursively()
                }
            }

    private fun copyAssets(path: String, target: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use(input::copyTo) }
        } else {
            target.mkdirs()
            children.forEach { copyAssets("$path/$it", File(target, it)) }
        }
    }
}
