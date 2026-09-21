package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `manual credential validation does not overwrite active credential before success`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsViewModel.kt"
        ).readText().replace("\r\n", "\n")

        val validationCall = source.indexOf("val result = repo.validateCredential(credential)")
        val successBranch = source.indexOf("is Result.Success -> {", startIndex = validationCall)
        val canonicalCopy = source.indexOf(
            "credential.copy(accessToken = checkNotNull(canonicalOpenCodeCookieOrNull(credential.accessToken)))",
            startIndex = successBranch
        )
        val saveCredential = source.indexOf("prefsManager.saveCredential(service, credentialToSave)", startIndex = successBranch)
        val connectedHealth = source.indexOf(
            "connectionHealthStore.update(service, ConnectionHealth.CONNECTED)",
            startIndex = successBranch
        )
        val failureBranch = source.indexOf("is Result.Failure ->", startIndex = successBranch)
        val cookieBranch = source.indexOf("accessToken = if (service == AiService.OPENCODE_GO)")
        val rawCookie = source.indexOf("state.accessToken", startIndex = cookieBranch)
        val otherProviderBranch = source.indexOf("} else {", startIndex = rawCookie)
        val trimmedToken = source.indexOf("state.accessToken.trim()", startIndex = otherProviderBranch)

        assertTrue("manual validation must validate the candidate credential", validationCall >= 0)
        assertTrue(
            "OpenCode Go validation must preserve raw cookie control characters",
            cookieBranch >= 0 && rawCookie > cookieBranch &&
                otherProviderBranch > rawCookie && trimmedToken > otherProviderBranch
        )
        assertTrue("manual validation must save only from the success branch", successBranch > validationCall)
        assertTrue("OpenCode Go validation success must canonicalize the persisted cookie", canonicalCopy > successBranch)
        assertTrue("manual validation must persist the credential after success", saveCredential > successBranch)
        assertTrue(
            "manual validation must mark the saved credential connected only in the success branch",
            connectedHealth > saveCredential && connectedHealth < failureBranch
        )
        assertTrue("manual validation must not save from the failure branch", failureBranch == -1 || saveCredential < failureBranch)
        assertFalse(source.contains("val result = repo.validateCredential()\n"))
    }

    @Test
    fun `device account linking validates transient credential before persistence`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsViewModel.kt"
        ).readText().replace("\r\n", "\n")
        val deviceFlow = source
            .substringAfter("fun startAccountLink(service: AiService)")
            .substringBefore("\n    fun setRefreshInterval")

        val transientValidation = deviceFlow.indexOf(
            "repositoryFor(service).validateCredential(credential)"
        )
        val successBranch = deviceFlow.indexOf("is Result.Success -> {", transientValidation)
        val persistence = deviceFlow.indexOf(
            "prefsManager.saveCredential(service, credential)",
            successBranch
        )
        val cancellationCatch = deviceFlow.indexOf("catch (e: CancellationException)")
        val generalCatch = deviceFlow.indexOf("catch (e: Exception)")

        assertTrue(transientValidation >= 0)
        assertTrue(successBranch > transientValidation)
        assertTrue(persistence > successBranch)
        assertTrue(cancellationCatch > persistence)
        assertTrue(generalCatch > cancellationCatch)
        assertFalse(deviceFlow.contains("repositoryFor(service).validateCredential()"))
        assertFalse(deviceFlow.contains("prefsManager.deleteCredential(service)"))
    }
}
