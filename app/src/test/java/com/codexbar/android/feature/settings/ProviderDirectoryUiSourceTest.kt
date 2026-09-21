package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDirectoryUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun stringResource(source: String, name: String): String =
        Regex("""<string name=\"$name\">(.*?)</string>""")
            .find(source)
            ?.groupValues
            ?.get(1)
            .orEmpty()

    @Test
    fun `provider directory is searchable filterable and single-expand`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("ProviderDirectoryControls("))
        assertTrue(source.contains("R.string.provider_search_label"))
        assertTrue(source.contains("ProviderConnectionFilter.entries.forEachIndexed"))
        assertTrue(source.contains("ProviderCategoryFilter.entries.forEach"))
        assertTrue(source.contains("var expandedProviderName by rememberSaveable"))
        assertTrue(source.contains("expanded = expandedProviderName == service.name"))
        assertTrue(source.contains("if (expanded) {"))
    }

    @Test
    fun `provider rows expose category auth and connection state while collapsed`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("service.providerMetadata.category.labelRes()"))
        assertTrue(source.contains("service.providerMetadata.authMode.labelRes()"))
        assertTrue(source.contains("R.string.status_connected"))
        assertTrue(source.contains("R.string.status_not_connected"))
    }

    @Test
    fun `Connections source includes OpenCode Go setup guide`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("service == AiService.OPENCODE_GO -> ProviderSecretSetupGuide"))
    }

    @Test
    fun `OpenCode Go setup guides use exact title and document security`() {
        val english = File(appDir, "src/main/res/values/strings.xml").readText()
        val japanese = File(appDir, "src/main/res/values-ja/strings.xml").readText()

        assertEquals("OpenCode Go web session", stringResource(english, "credential_opencode_go_setup_title"))
        assertEquals("OpenCode Go Webセッション", stringResource(japanese, "credential_opencode_go_setup_title"))

        listOf(
            english to listOf(
                "Cookie is sent only to the fixed opencode.ai host, redirects are disabled, and it is encrypted with Android Keystore only after validation.",
                "Workspace ID is optional and accepts a wrk_... ID or an OpenCode workspace URL."
            ),
            japanese to listOf(
                "Cookieは固定のopencode.aiにのみ送信され、リダイレクトは無効化され、検証後にのみAndroid Keystoreで暗号化されます。",
                "ワークスペースIDは任意で、wrk_... IDまたはOpenCodeワークスペースURLを受け付けます。"
            )
        ).forEach { (source, expectedText) ->
            stringResource(source, "credential_opencode_go_setup_body").let { body ->
                expectedText.forEach { expected -> assertTrue(body.contains(expected)) }
            }
        }
    }
}
