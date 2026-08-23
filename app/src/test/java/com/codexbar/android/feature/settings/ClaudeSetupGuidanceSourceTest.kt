package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSetupGuidanceSourceTest {
    private val rootDir: File = listOf(File("."), File(".."))
        .first { File(it, "README.md").isFile }
    private val appDir = File(rootDir, "app")

    @Test
    fun `Claude setup defaults to official CLI companion instead of setup-token`() {
        val screen = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")
        val readme = File(rootDir, "README.md").readText().replace("\r\n", "\n")
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        val companionIndex = File(rootDir, "companion/claude/src/index.js").readText()
        val releaseWorkflow = File(rootDir, ".github/workflows/release.yml").readText()
        val proguardRules = File(appDir, "proguard-rules.pro").readText()
        val mainSource = File(appDir, "src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertTrue(screen.contains("service == AiService.CLAUDE -> ClaudeCompanionSetup"))
        assertFalse(screen.contains("credential_claude_manual_warning"))
        assertFalse(screen.contains("CLAUDE_SETUP_COMMAND"))
        assertFalse(mainSource.contains("Credential.ClaudeCredential"))
        assertFalse(mainSource.contains("ClaudeApiService"))
        assertFalse(mainSource.contains("ClaudeTokenRefreshService"))
        assertFalse(mainSource.contains("api/oauth/usage"))
        assertTrue(mainSource.contains("containsLegacyClaudeTokens"))
        assertTrue(screen.contains("GmsBarcodeScanning.getClient"))
        assertTrue(screen.contains("private fun startClaudePairingScan("))
        val connectionsSetup = screen.substring(
            screen.indexOf("fun ConnectionsScreen("),
            screen.indexOf("private fun startClaudePairingScan(")
        )
        assertFalse(connectionsSetup.contains("GmsBarcodeScanning.getClient"))
        assertTrue(screen.contains("runCatching {\n        val options = GmsBarcodeScannerOptions.Builder()"))
        assertTrue(proguardRules.contains("-keep class com.google.mlkit.** { *; }"))
        assertTrue(
            proguardRules.contains(
                "-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }"
            )
        )
        assertFalse(manifest.contains("claude-pair"))
        assertFalse(companionIndex.contains("codexbar://"))
        assertTrue(companionIndex.contains("CBCLAUDE1"))
        assertTrue(readme.contains("Do **not** paste the result of `claude setup-token`"))
        assertFalse(readme.contains("advanced compatibility path"))
        assertTrue(releaseWorkflow.contains("package-lock.json scripts src test"))
        assertTrue(releaseWorkflow.contains("Smoke test packaged Claude companion"))
    }
}
