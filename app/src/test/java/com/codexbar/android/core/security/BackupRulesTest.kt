package com.codexbar.android.core.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.widget.WidgetPrefsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {

    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `manifest declares both legacy and Android 12 backup rules`() {
        val manifest = parseXml(File(appDir, "src/main/AndroidManifest.xml")).documentElement
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals("true", application.getAttribute("android:allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun `legacy backup rules exclude secure credential stores`() {
        val rules = parseXml(File(appDir, "src/main/res/xml/backup_rules.xml"))
        val excludes = rules.getElementsByTagName("exclude").asElements()

        assertSecurePrefsExcluded(excludes)
    }

    @Test
    fun `data extraction rules exclude encrypted credentials from backup and transfer`() {
        val rules = parseXml(File(appDir, "src/main/res/xml/data_extraction_rules.xml")).documentElement
        val cloudBackup = (rules.getElementsByTagName("cloud-backup").item(0) as Element)
            .getElementsByTagName("exclude")
            .asElements()
        val deviceTransfer = (rules.getElementsByTagName("device-transfer").item(0) as Element)
            .getElementsByTagName("exclude")
            .asElements()

        assertSecurePrefsExcluded(cloudBackup)
        assertSecurePrefsExcluded(deviceTransfer)
    }

    private fun assertSecurePrefsExcluded(excludes: List<Element>) {
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == "${EncryptedPrefsManager.SECURE_PREFS_NAME}.xml"
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == TokenRefreshStateStore.BACKUP_PATH
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == ConnectionHealthStore.BACKUP_PATH
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == MonitoringSessionStore.BACKUP_PATH
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == WidgetPrefsManager.BACKUP_PATH
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "sharedpref" &&
                    exclude.getAttribute("path") == QuotaHistoryStore.BACKUP_PATH
            }
        )
        assertTrue(
            excludes.any { exclude ->
                exclude.getAttribute("domain") == "file" &&
                    exclude.getAttribute("path") == EncryptedPrefsManager.SECURE_DATASTORE_BACKUP_PATH
            }
        )
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length)
            .map { item(it) }
            .filterIsInstance<Element>()
    }
}
