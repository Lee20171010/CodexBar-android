package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodePayloadParserTest {
    @Test
    fun `Seroval decimal percentages retain percent units including one percent`() {
        for (percent in listOf(0.0, 0.5, 1.0, 12.5, 99.9, 100.0)) {
            val usage = OpenCodePayloadParser.parseSubscription(
                """rollingUsage:${'$'}R[42]={status:"ok",resetInSec:600,usagePercent:$percent}"""
            )
            assertEquals("percent=$percent", percent / 100.0, usage.windows.single().utilization, 0.000001)
        }
    }

    @Test
    fun `JSON decimal numbers and strings preserve dashboard fraction compatibility`() {
        for ((value, expected) in listOf("12.5" to 0.125, "\"12.5\"" to 0.125, "0.25" to 0.25)) {
            val usage = OpenCodePayloadParser.parseSubscription(
                """{"rollingUsage":{"usagePercent":$value,"resetInSec":600}}"""
            )
            assertEquals(expected, usage.windows.single().utilization, 0.000001)
        }
    }

    @Test
    fun `computed decimal totals preserve sub one percent usage across encodings`() {
        for (text in listOf(
            """{"rollingUsage":{"used":0.5,"limit":100,"resetInSec":600}}""",
            """rollingUsage:${'$'}R[42]={used:0.5,limit:100,resetInSec:600}"""
        )) {
            assertEquals(0.005, OpenCodePayloadParser.parseSubscription(text).windows.single().utilization, 0.000001)
        }
    }

    // --- parseSubscription ---

    @Test
    fun `parser maps rolling, weekly, and renewal values`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"rollingUsage":{"usagePercent":25,"resetInSec":3600},"weeklyUsage":{"usagePercent":50,"resetInSec":7200},"renewAt":"2026-08-01T00:00:00Z"}""",
            now = Instant.parse("2026-07-26T00:00:00Z")
        )

        assertEquals(listOf("5-Hour", "Weekly"), usage.windows.map(UsageWindow::label))
        assertEquals(0.25, usage.windows[0].utilization, 0.0001)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), usage.renewsAt)
    }

    @Test
    fun `parser maps Seroval hydration windows`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """${'$'}R[28](${'$'}R[18],${'$'}R[29]={mine:!0,useBalance:!1,rollingUsage:${'$'}R[31]={status:"ok",resetInSec:1725,usagePercent:100},weeklyUsage:${'$'}R[32]={status:"ok",resetInSec:579422,usagePercent:60},monthlyUsage:${'$'}R[33]={status:"ok",resetInSec:2598311,usagePercent:80}});""",
            now = Instant.parse("2026-07-27T00:00:00Z")
        )

        assertEquals(listOf("5-Hour", "Weekly", "Monthly"), usage.windows.map(UsageWindow::label))
        assertEquals(1.0, usage.windows[0].utilization, 0.0001)
    }

    @Test
    fun `parser maps nested alternate Go windows`() {
        val now = Instant.parse("2026-07-26T00:00:00Z")
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"payload":{"usage":{"rollingWindow":{"usedPercent":25,"resetInSeconds":60},"weeklyWindow":{"percentUsed":0.5,"resetAt":"2026-07-26T00:02:00Z"},"monthlyWindow":{"used":1,"limit":4,"resetsAt":1785024300}}}}""",
            now = now
        )

        assertEquals(listOf("5-Hour", "Weekly", "Monthly"), usage.windows.map(UsageWindow::label))
        assertEquals(0.25, usage.windows[0].utilization, 0.0001)
    }

    @Test
    fun `parser maps windows wrapped inside named usage objects`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"rollingUsage":{"window":{"usagePercent":25,"resetInSec":60}},"weeklyUsage":{"window":{"usagePercent":50,"resetInSec":120}}}"""
        )

        assertEquals(listOf("5-Hour", "Weekly"), usage.windows.map(UsageWindow::label))
        assertEquals(0.25, usage.windows[0].utilization, 0.0001)
        assertEquals(0.5, usage.windows[1].utilization, 0.0001)
    }

    @Test
    fun `parser maps window values inside named arrays`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"rollingUsage":[{"usagePercent":25,"resetInSec":60}],"weeklyUsage":[{"usagePercent":50,"resetInSec":120}]}"""
        )

        assertEquals(listOf("5-Hour", "Weekly"), usage.windows.map(UsageWindow::label))
        assertEquals(0.25, usage.windows[0].utilization, 0.0001)
        assertEquals(0.5, usage.windows[1].utilization, 0.0001)
    }

    @Test
    fun `parser prefers renewal next to nested usage`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"renewAt":"2026-09-01T00:00:00Z","usage":{"renewAt":"2026-08-01T00:00:00Z","rollingUsage":{"usagePercent":25,"resetInSec":60}}}"""
        )

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), usage.renewsAt)
    }

    @Test
    fun `parser does not borrow renewal from a later rolling candidate`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"plans":[{"rollingUsage":{"usagePercent":25,"resetInSec":60}},{"renewAt":"2026-08-01T00:00:00Z","rollingUsage":{"usagePercent":75,"resetInSec":120}}]}"""
        )

        assertEquals(0.25, usage.windows.single().utilization, 0.0001)
        assertNull(usage.renewsAt)
    }

    @Test
    fun `Seroval fallback does not borrow renewal from another plan`() {
        val usage = OpenCodePayloadParser.parseSubscription(
            """plans:[{rollingUsage:{usagePercent:25,resetInSec:60}},{renewAt:"2026-08-01T00:00:00Z",rollingUsage:{usagePercent:75,resetInSec:120}}]"""
        )

        assertEquals(0.25, usage.windows.single().utilization, 0.0001)
        assertNull(usage.renewsAt)
    }

    @Test
    fun `parser throws on missing rolling usage and prefers real JSON over string content`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenCodePayloadParser.parseSubscription("""{"note":"rollingUsage:{usagePercent:10}"}""")
        }
        val usage = OpenCodePayloadParser.parseSubscription(
            """{"note":"rollingUsage:{usagePercent:99}","rollingUsage":{"usagePercent":10,"resetInSec":60}}"""
        )
        assertEquals(0.1, usage.windows.single().utilization, 0.0001)
    }

    // --- parseZenBalance ---

    @Test
    fun `zen balance accepts valid fields and rejects invalid`() {
        assertEquals(12.5, OpenCodePayloadParser.parseZenBalance("""{"zenBalance":12.5}""")?.amount ?: -1.0, 0.0001)
        assertEquals(7.25, OpenCodePayloadParser.parseZenBalance("""{"currentBalanceUSD":7.25}""")?.amount ?: -1.0, 0.0001)
        assertEquals(1042.75, OpenCodePayloadParser.parseZenBalance("""{"zenBalance":" 1,042.75 "}""")?.amount ?: -1.0, 0.0001)

        listOf(
            """{"balance":0}""",
            """{"zenBalance":"NaN"}""",
            """{"zenBalance":"-1"}""",
            "zenBalance=1e3oops",
        ).forEach { assertNull(it, OpenCodePayloadParser.parseZenBalance(it)) }
    }

    @Test
    fun `zen HTML balance requires label and rejects unlabeled`() {
        assertEquals(1234.56, OpenCodePayloadParser.parseZenBalance("<section><span>Zen balance</span><strong>$1,234.56</strong></section>")?.amount ?: -1.0, 0.0001)
        assertNull(OpenCodePayloadParser.parseZenBalance("<strong>$1,234.56</strong>"))
    }

    @Test
    fun `zen HTML balance parses multiline label with spaced grouped dollars`() {
        val payload = """
            <section>
                <span>Zen balance</span>
                <strong>${'$'} 1,234.56</strong>
            </section>
        """.trimIndent()

        assertEquals(1234.56, OpenCodePayloadParser.parseZenBalance(payload)?.amount ?: -1.0, 0.0001)
    }

    @Test
    fun `zen HTML balance accepts Japanese label`() {
        val payload = """<span>現在の残高</span><strong>${'$'} 1,234.56</strong>"""

        assertEquals(1234.56, OpenCodePayloadParser.parseZenBalance(payload)?.amount ?: -1.0, 0.0001)
    }

    // --- parseBillingZenBalance ---

    @Test
    fun `billing balance maps Seroval customer object and rejects invalid`() {
        assertEquals(12.5, OpenCodePayloadParser.parseBillingZenBalance(
            """${'$'}R[1]={customerID:"cus_test",balance:${'$'}R[2]=1250000000}"""
        )?.amount ?: -1.0, 0.0001)

        listOf(
            """${'$'}R[1]={customerID:"   ","balance":${'$'}R[2]=1250000000}""",
            "balance:1250000000",
            """notcustomerID:"cus_test",notbalance:1250000000""",
        ).forEach { assertNull(it, OpenCodePayloadParser.parseBillingZenBalance(it)) }
    }

    // --- payloadDiagnostic ---

    @Test
    fun `diagnostic excludes sensitive values and classifies response types`() {
        val payload = """{"email":"private@x.invalid","token":"t","rollingUsage":{"usagePercent":25}}"""
        val diagnostic = OpenCodePayloadParser.payloadDiagnostic(payload)
        assertTrue(diagnostic.contains("response=json"))
        assertFalse(diagnostic.contains("private"))
        val htmlDiagnostic = OpenCodePayloadParser.payloadDiagnostic("<html><body>private</body></html>")
        assertTrue(htmlDiagnostic.startsWith("response=html"))
        assertEquals("response=empty, chars=0, keys=[]", OpenCodePayloadParser.payloadDiagnostic(""))
    }
}
