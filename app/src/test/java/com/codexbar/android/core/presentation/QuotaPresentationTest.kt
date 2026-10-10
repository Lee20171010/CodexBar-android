package com.codexbar.android.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class QuotaPresentationTest {
    @Test fun `displayPercent follows fillUsed semantics and clamps consistently`() {
        assertEquals(65, QuotaPresentation.displayPercent(0.35, fillUsed = false))
        assertEquals(35, QuotaPresentation.displayPercent(0.35, fillUsed = true))
        assertEquals(100, QuotaPresentation.displayPercent(-0.5, fillUsed = false))
        assertEquals(0, QuotaPresentation.displayPercent(-0.5, fillUsed = true))
        assertEquals(0, QuotaPresentation.displayPercent(1.5, fillUsed = false))
        assertEquals(100, QuotaPresentation.displayPercent(1.5, fillUsed = true))
        // Overquota clamps like the gauge fill instead of showing 110% used or -10% left.
        assertEquals(100, QuotaPresentation.displayPercent(1.10, fillUsed = true))
        assertEquals(0, QuotaPresentation.displayPercent(1.10, fillUsed = false))
    }

    private fun strings(path: String): Map<String, String> {
        val file = listOf(File("src/main/res/$path"), File("app/src/main/res/$path")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    // QuotaDetail always passes (days, hours, minutes) to whichever pace_out_* string it picks.
    @Test fun `pace eta strings format days hours minutes in en`() {
        val s = strings("values/strings.xml")
        assertEquals("Est. out in 2d 3h", String.format(Locale.ENGLISH, s.getValue("pace_out_days"), 2, 3, 10))
        assertEquals("Est. out in 5h 30m", String.format(Locale.ENGLISH, s.getValue("pace_out_hours"), 0, 5, 30))
        assertEquals("Est. out in 45m", String.format(Locale.ENGLISH, s.getValue("pace_out_minutes"), 0, 0, 45))
    }

    @Test fun `pace eta strings format days hours minutes in zh-rTW`() {
        val s = strings("values-zh-rTW/strings.xml")
        assertEquals("預計 2 天 3 小時後用完", String.format(Locale.ENGLISH, s.getValue("pace_out_days"), 2, 3, 10))
        assertEquals("預計 5 小時 30 分鐘後用完", String.format(Locale.ENGLISH, s.getValue("pace_out_hours"), 0, 5, 30))
        assertEquals("預計 45 分鐘後用完", String.format(Locale.ENGLISH, s.getValue("pace_out_minutes"), 0, 0, 45))
    }

    @Test fun `percent used and left exist in both languages`() {
        val en = strings("values/strings.xml")
        val zh = strings("values-zh-rTW/strings.xml")
        assertEquals("35% used", String.format(Locale.ENGLISH, en.getValue("percent_used"), 35))
        assertEquals("65% left", String.format(Locale.ENGLISH, en.getValue("percent_left"), 65))
        assertEquals("已使用 35%", String.format(Locale.ENGLISH, zh.getValue("percent_used"), 35))
        assertEquals("剩餘 65%", String.format(Locale.ENGLISH, zh.getValue("percent_left"), 65))
    }
}
