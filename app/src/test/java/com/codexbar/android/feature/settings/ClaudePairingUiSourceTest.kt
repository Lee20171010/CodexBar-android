package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudePairingUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private val screen: String = sourceFile("SettingsScreen.kt")
    private val viewModel: String = sourceFile("SettingsViewModel.kt")

    @Test
    fun `a whole pairing code pairs without a second tap`() {
        val import = viewModel.substring(
            viewModel.indexOf("fun importClaudePairingCode("),
            viewModel.indexOf("fun reportClaudePairingClipboardEmpty(")
        )

        assertTrue(import.contains("updateClaudePairingCode(value)"))
        assertTrue(import.contains("connectClaudeCompanion()"))
    }

    @Test
    fun `the clipboard is an alternative to the in-app scanner`() {
        val clipboard = screen.substring(
            screen.indexOf("private fun pairingCodeFromClipboard("),
            screen.indexOf("private fun startClaudePairingScan(")
        )

        assertTrue(screen.contains("onPastePairing = onPasteClaudePairing"))
        assertTrue(screen.contains("R.string.action_paste_claude_pairing"))
        assertTrue(clipboard.contains("ClaudeCompanionPairing.PREFIX"))
        // Only the pairing token is taken, so surrounding pasted text is not rejected.
        assertTrue(clipboard.contains("split(Regex(\"\\\\s+\"))"))
        assertTrue(viewModel.contains("fun reportClaudePairingClipboardEmpty()"))
        assertTrue(viewModel.contains("R.string.validation_claude_clipboard_empty"))
    }

    @Test
    fun `a manually entered pairing code can be verified on screen`() {
        val setup = screen.substring(screen.indexOf("private fun ClaudeCompanionSetup("))

        assertTrue(setup.contains("var pairingCodeVisible by rememberSaveable"))
        assertTrue(setup.contains("VisualTransformation.None"))
        assertTrue(setup.contains("PasswordVisualTransformation()"))
        assertTrue(setup.contains("R.string.action_show_pairing_code"))
        assertTrue(setup.contains("R.string.action_hide_pairing_code"))
    }

    private fun sourceFile(name: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/$name"
        ).readText().replace("\r\n", "\n")
    }
}
