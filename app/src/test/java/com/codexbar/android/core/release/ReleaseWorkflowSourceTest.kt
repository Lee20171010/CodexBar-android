package com.codexbar.android.core.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWorkflowSourceTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `tagged releases require versioned notes and publish as stable latest`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()

        assertTrue(workflow.contains("docs/releases/"))
        assertTrue(workflow.contains("GITHUB_REF_NAME"))
        assertTrue(workflow.contains("test -f \"\$notes_file\""))
        assertTrue(workflow.contains("--notes-file"))
        assertTrue(workflow.contains("--verify-tag"))
        assertTrue(workflow.contains("--latest"))
        assertFalse(workflow.contains("--prerelease"))
    }

    @Test
    fun `release signing material is scoped to the build step and deleted`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()
        val buildStep = workflow
            .substringAfter("- name: Build signed release artifacts")
            .substringBefore("- name: Verify release signing identity")

        assertTrue(buildStep.contains("ANDROID_KEYSTORE_BASE64: \${{ secrets.ANDROID_KEYSTORE_BASE64 }}"))
        assertTrue(buildStep.contains("trap cleanup EXIT"))
        assertTrue(buildStep.contains("rm -f \"\$keystore_path\""))
        assertTrue(buildStep.contains("chmod 600 \"\$keystore_path\""))
        assertFalse(workflow.contains("GITHUB_ENV"))
    }

    @Test
    fun `signed release is cold started before publication`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()
        val smokeStep = workflow
            .substringAfter("- name: Smoke test signed release APK")
            .substringBefore("- name: Prepare release bundle")
        val smokeScript = File(repoDir, ".github/scripts/smoke-test-release.sh").readText()

        assertTrue(smokeStep.contains("ReactiveCircus/android-emulator-runner@"))
        assertTrue(smokeStep.contains("api-level: 36"))
        assertTrue(smokeStep.contains("script: sh .github/scripts/smoke-test-release.sh"))
        assertFalse(smokeStep.contains("script: |"))
        assertFalse(smokeStep.contains("for candidate"))
        assertTrue(smokeScript.contains("set -eu"))
        assertFalse(smokeScript.contains("pipefail"))
        assertFalse(smokeScript.contains("mapfile"))
        assertFalse(smokeScript.contains("< <("))
        assertTrue(smokeScript.contains("adb install"))
        assertTrue(smokeScript.contains("adb shell am start -W"))
        assertTrue(smokeScript.contains("adb shell pidof"))
        assertTrue(smokeScript.contains("adb logcat -b crash -d"))
        assertTrue(smokeScript.contains("-d 'codexbar://connections'"))
        assertTrue(
            smokeScript.contains("Release app process exited after opening Connections.")
        )
        assertTrue(smokeScript.contains("Release app crashed after opening Connections."))
        assertTrue(
            workflow.indexOf("- name: Smoke test signed release APK") <
                workflow.indexOf("- name: Publish GitHub Release")
        )
    }

    @Test
    fun `release runner enables hardware acceleration before emulator startup`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()
        val kvmStep = workflow
            .substringAfter("- name: Enable KVM access")
            .substringBefore("- name: Smoke test signed release APK")

        assertTrue(kvmStep.contains("sudo chmod 666 /dev/kvm"))
        assertTrue(kvmStep.contains("test -r /dev/kvm"))
        assertTrue(kvmStep.contains("test -w /dev/kvm"))
        assertTrue(
            workflow.indexOf("- name: Enable KVM access") <
                workflow.indexOf("- name: Smoke test signed release APK")
        )
    }

    @Test
    fun `Gemini companion is tested and shipped with an SBOM`() {
        val continuousIntegration = File(repoDir, ".github/workflows/android.yml").readText()
        val release = File(repoDir, ".github/workflows/release.yml").readText()
        val bundleStep = release
            .substringAfter("- name: Prepare release bundle")
            .substringBefore("- name: Attest release artifacts")

        assertTrue(continuousIntegration.contains("gemini-companion:"))
        assertTrue(continuousIntegration.contains("npm audit --omit=dev"))
        assertTrue(release.contains("- name: Verify Gemini companion"))
        assertTrue(bundleStep.contains("CodexBar-Gemini-Companion-\${GITHUB_REF_NAME}.zip"))
        assertTrue(bundleStep.contains("npm sbom --omit=dev --sbom-format=cyclonedx"))
        assertTrue(bundleStep.contains("gemini-companion-sbom.cdx.json"))
    }

    @Test
    fun `Codex telemetry companion is tested and shipped with an SBOM`() {
        val continuousIntegration = File(repoDir, ".github/workflows/android.yml").readText()
        val release = File(repoDir, ".github/workflows/release.yml").readText()
        val bundleStep = release
            .substringAfter("- name: Prepare release bundle")
            .substringBefore("- name: Attest release artifacts")

        assertTrue(continuousIntegration.contains("codex-companion:"))
        assertTrue(continuousIntegration.contains("companion/codex"))
        assertTrue(release.contains("- name: Verify Codex telemetry companion"))
        assertTrue(
            bundleStep.contains(
                "CodexBar-Codex-Telemetry-Companion-\${GITHUB_REF_NAME}.zip"
            )
        )
        assertTrue(bundleStep.contains("codex-telemetry-companion-sbom.cdx.json"))
    }

    @Test
    fun `versioned emulator screenshots are published as release assets`() {
        val release = File(repoDir, ".github/workflows/release.yml").readText()
        val bundleStep = release
            .substringAfter("- name: Prepare release bundle")
            .substringBefore("- name: Attest release artifacts")

        assertTrue(bundleStep.contains("docs/images/releases/\${GITHUB_REF_NAME}"))
        assertTrue(bundleStep.contains("cp \"\$screenshots_dir\"/*.png build/release/"))
    }
}
