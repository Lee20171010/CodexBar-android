package com.codexbar.android

import com.codexbar.android.core.data.DeepSeekRepositoryImpl
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.di.NetworkModule

import android.os.Bundle
import android.os.Process
import android.content.Intent
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.codexbar.android.core.nativecli.CliProcess
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject

/** Synthetic, fixed commands only; included only in native acceptance variants. */
class NativeCliSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Running native Core/CLI self-test…"
            textSize = 18f
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(Button(this@NativeCliSmokeActivity).apply {
                text = "Open dashboard / OpenCode Go settings"
                setOnClickListener {
                    startActivity(Intent(this@NativeCliSmokeActivity, MainActivity::class.java))
                }
            })
        }
        val scroll = ScrollView(this).apply { addView(content) }
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        setContentView(scroll)
        val runId = intent.getStringExtra("run_id") ?: UUID.randomUUID().toString()
        lifecycleScope.launch {
            val report = runInterruptible(Dispatchers.IO) { runProbes() }.put("runId", runId)
            for ((flag, service, name) in listOf(
                Triple("test_go", AiService.OPENCODE_GO, "opencode-go-api-invalid-key"),
                Triple("test_openrouter", AiService.OPENROUTER, "openrouter-api-invalid-key"),
                Triple("test_copilot", AiService.COPILOT, "copilot-api-invalid-token"),
                Triple("test_deepseek", AiService.DEEPSEEK, "deepseek-api-invalid-key")
            )) {
                if (!intent.getBooleanExtra(flag, false)) continue
                // Exercise the production client with an intentionally invalid key, never a saved account.
                val result = if (service == AiService.DEEPSEEK) {
                    DeepSeekRepositoryImpl(NetworkModule.provideDeepSeekApiService(NetworkModule.provideJson()))
                        .fetchQuota(CredentialSession(AccountConnection.create(service),
                            Credential.DeepSeekCredential("codexbar-invalid-synthetic-key")))
                } else NativeCodexBarClient(applicationContext).fetchApiKey(service, "codexbar-invalid-synthetic-key")
                val passed = result is Result.Failure && result.error is AppError.AuthError &&
                    result.error.service == service
                report.getJSONArray("results").put(JSONObject()
                    .put("name", name).put("passed", passed)
                    .put("resultType", if (result is Result.Failure) result.error.javaClass.simpleName else "UnexpectedSuccess"))
                report.put("passed", report.getBoolean("passed") && passed)
            }
            File(filesDir, "native-report.json").writeText(report.toString(2))
            // Fixed synthetic probes only. Allows verification of the exact non-debuggable APK.
            Log.i("CodexBarNative", report.toString())
            val results = report.getJSONArray("results")
            status.text = buildString {
                appendLine("Native CLI: ${if (report.getBoolean("passed")) "PASS" else "FAIL"}")
                for (i in 0 until results.length()) {
                    val result = results.getJSONObject(i)
                    appendLine("${result.getString("name")}: ${if (result.getBoolean("passed")) "PASS" else "FAIL"}")
                }
                if (report.has("error")) appendLine(report.getString("error"))
                appendLine("\nSelf-test uses no saved credentials. Open the dashboard and add your OpenCode Go API key in Settings to query live quota.")
            }
        }
    }

    private fun runProbes(): JSONObject {
        val report = JSONObject().put("uid", Process.myUid()).put("passed", false)
        val results = JSONArray()
        report.put("results", results)
        try {
            check(android.os.Build.VERSION.SDK_INT >= 28) { "Native CLI requires API 28" }
            val home = File(filesDir, "native-smoke").apply { mkdirs() }
            copyAssets("codexbar", home)
            val config = File(home, "config.json").apply {
                writeText("""{"version":1,"providers":[{"id":"codex","enabled":false}]}""")
            }
            val env = mutableMapOf<String, String>()
            for (key in listOf("ANDROID_ROOT", "ANDROID_DATA", "ANDROID_ART_ROOT", "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT")) {
                System.getenv(key)?.let { env[key] = it }
            }
            env.putAll(mapOf(
                "HOME" to home.path,
                "CODEXBAR_CONFIG" to config.path,
                "CODEX_HOME" to File(home, ".codex").path,
                "CLAUDE_CONFIG_DIR" to File(home, ".claude").path,
                "XDG_CONFIG_HOME" to File(home, ".config").path,
                "XDG_CACHE_HOME" to File(home, ".cache").path,
                "XDG_DATA_HOME" to File(home, ".local/share").path,
                "TMPDIR" to cacheDir.path,
                "PATH" to "/system/bin",
                "LD_LIBRARY_PATH" to applicationInfo.nativeLibraryDir,
                "CODEXBAR_RESOURCE_BUNDLE_PATH" to File(home, "CodexBar_CodexBarCore.bundle").path,
            ))
            val binary = File(applicationInfo.nativeLibraryDir, "libcodexbar.so")
            fun probe(name: String, args: List<String>, extra: Map<String, String> = emptyMap(),
                      verify: (JSONObject) -> Boolean) {
                val capture = CliProcess.run(binary, args, env + extra, home, 45_000)
                val result = JSONObject().put("name", name)
                    .put("exitCode", capture.exitCode).put("timedOut", capture.timedOut)
                    .put("reaped", true).put("durationMs", capture.durationMs)
                    .put("stdout", capture.stdout).put("stderr", capture.stderr)
                    .put("stdoutTruncated", capture.stdoutTruncated).put("stderrTruncated", capture.stderrTruncated)
                result.put("passed", !result.getBoolean("timedOut") && result.getBoolean("reaped") &&
                    !result.getBoolean("stdoutTruncated") && !result.getBoolean("stderrTruncated") && verify(result))
                results.put(result)
            }
            probe("version", listOf("--version")) {
                it.getInt("exitCode") == 0 && it.getString("stdout").contains("CodexBar")
            }
            probe("resources", emptyList(), mapOf("CODEXBAR_RESOURCE_SMOKE" to "1")) {
                it.getInt("exitCode") == 0 && it.getString("stdout").contains("CODEXBAR_RESOURCE_SMOKE_OK")
            }
            probe("config", listOf("config", "validate")) {
                it.getInt("exitCode") == 0
            }
            probe("missing-credentials", listOf("usage", "--provider", "codex", "--source", "oauth",
                "--json")) {
                val output = JSONArray(it.getString("stdout"))
                it.getInt("exitCode") == 1 && output.length() == 1 &&
                    output.getJSONObject(0).getString("provider") == "codex" &&
                    output.getJSONObject(0).getJSONObject("error").getString("kind") == "provider"
            }
            report.put("passed", (0 until results.length()).all { results.getJSONObject(it).getBoolean("passed") })
        } catch (error: Exception) {
            if (error is InterruptedException) throw error
            report.put("error", "${error.javaClass.simpleName}: ${error.message}")
        }
        return report
    }

    private fun copyAssets(path: String, target: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input -> target.outputStream().use(input::copyTo) }
        } else {
            target.mkdirs()
            children.forEach { copyAssets("$path/$it", File(target, it)) }
        }
    }
}
