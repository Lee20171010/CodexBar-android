package com.codexbar.android.core.notification

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NotificationLayoutTest {

    // The custom notification content view renders on the SystemUI background, so text must
    // use the androidx notification text appearances (which delegate to the platform
    // TextAppearance.Material.Notification* the notification templates themselves use).
    // Fixed @android:color/* or #hex colors stay light-theme dark text and go unreadable
    // in dark mode.
    @Test fun `notification text views use notification text appearances, never fixed colors`() {
        val file = listOf(File("src/main/res/layout/notification_compact.xml"),
            File("app/src/main/res/layout/notification_compact.xml")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("TextView")
        assertTrue("layout must contain text views", nodes.length > 0)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            assertNull("explicit textColor override reintroduces fixed-theme text: " +
                el.attributes.getNamedItem("android:id")?.nodeValue,
                el.attributes.getNamedItem("android:textColor"))
            val appearance = el.attributes.getNamedItem("android:textAppearance")?.nodeValue
            assertTrue("TextView ${el.attributes.getNamedItem("android:id")?.nodeValue} needs a " +
                "notification text appearance, was: $appearance",
                appearance != null && appearance.startsWith("@style/TextAppearance.Compat.Notification"))
        }
    }
}
