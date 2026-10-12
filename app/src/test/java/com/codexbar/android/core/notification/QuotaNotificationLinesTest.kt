package com.codexbar.android.core.notification

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.ReportedMoney
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.presentation.QuotaFailure
import com.codexbar.android.core.presentation.QuotaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import java.time.Instant

class QuotaNotificationLinesTest {

    private val now = Instant.now()

    private val context: Context = Mockito.mock(Context::class.java).also {
        Mockito.`when`(it.getString(R.string.quota_unavailable)).thenReturn("Quota unavailable")
        Mockito.`when`(it.getString(R.string.reconnect_required)).thenReturn("Reconnect required")
        Mockito.`when`(it.getString(R.string.stale_short)).thenReturn("Stale")
        Mockito.`when`(it.getString(R.string.percent_left, 75)).thenReturn("75% left")
        Mockito.`when`(it.getString(R.string.percent_left, 40)).thenReturn("40% left")
        Mockito.`when`(it.getString(R.string.percent_left, 50)).thenReturn("50% left")
        Mockito.`when`(it.getString(R.string.widget_more_accounts, 1)).thenReturn("+1 accounts · Open app")
    }

    private fun snapshot(
        service: AiService,
        utilization: Double,
        at: Instant = now,
        money: ReportedMoney? = null
    ) = QuotaSnapshot(
        QuotaInfo(service, listOf(
            UsageWindow("5-Hour", utilization, now.plusSeconds(3600), "primary", durationSeconds = 300),
            UsageWindow("Extra", 0.99, null, "supp", supplemental = true)
        ), null, fetchedAt = at, source = "native-oauth", money = money)
    )

    private fun connection(service: AiService, name: String? = null) =
        if (name == null) AccountConnection.create(service) else AccountConnection.create(service, name)

    @Test fun `no accounts produce no rows`() {
        assertEquals(emptyList<NotificationRow>(), notificationRows(context, emptyList(), now))
    }

    @Test fun `single account row shows provider and window value without age`() {
        val rows = notificationRows(context,
            listOf(connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25)), now)
        assertEquals(listOf(NotificationRow("Codex", "5-Hour 75% left")), rows)
    }

    @Test fun `every connection gets its own row in order`() {
        val quotas = listOf(
            connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25),
            connection(AiService.CLAUDE) to snapshot(AiService.CLAUDE, 0.60),
            connection(AiService.GEMINI) to snapshot(AiService.GEMINI, 0.25)
        )
        assertEquals(listOf(
            NotificationRow("Codex", "5-Hour 75% left"),
            NotificationRow("Claude", "5-Hour 40% left"),
            NotificationRow("Gemini", "5-Hour 75% left")
        ), notificationRows(context, quotas, now))
    }

    @Test fun `duplicate providers are disambiguated by name then occurrence`() {
        val quotas = listOf(
            connection(AiService.CODEX, "Work") to snapshot(AiService.CODEX, 0.25),
            connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25),
            connection(AiService.CODEX, "Home") to snapshot(AiService.CODEX, 0.25)
        )
        assertEquals(listOf(
            NotificationRow("Codex (Work)", "5-Hour 75% left"),
            NotificationRow("Codex", "5-Hour 75% left"),
            NotificationRow("Codex (Home)", "5-Hour 75% left")
        ), notificationRows(context, quotas, now))
    }

    @Test fun `identical names collide on the final label and get occurrence suffixes`() {
        val quotas = listOf(
            connection(AiService.CODEX, "Work") to snapshot(AiService.CODEX, 0.25),
            connection(AiService.CODEX, "Work") to snapshot(AiService.CODEX, 0.25),
            connection(AiService.CLAUDE) to snapshot(AiService.CLAUDE, 0.25),
            connection(AiService.CLAUDE) to snapshot(AiService.CLAUDE, 0.25)
        )
        assertEquals(listOf(
            NotificationRow("Codex (Work) #1", "5-Hour 75% left"),
            NotificationRow("Codex (Work) #2", "5-Hour 75% left"),
            NotificationRow("Claude #1", "5-Hour 75% left"),
            NotificationRow("Claude #2", "5-Hour 75% left")
        ), notificationRows(context, quotas, now))
    }

    @Test fun `stale reconnect unavailable and balance rows keep status markers`() {
        val quotas = listOf(
            connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25, at = now.minusSeconds(7200)),
            connection(AiService.CLAUDE) to QuotaSnapshot(
                snapshot(AiService.CLAUDE, 0.25).quota, failure = QuotaFailure.AUTH),
            connection(AiService.GEMINI) to QuotaSnapshot(),
            connection(AiService.OPENROUTER) to QuotaSnapshot(QuotaInfo(
                AiService.OPENROUTER, emptyList(), null, fetchedAt = now, source = "native-oauth",
                money = ReportedMoney(12.5, 3.0, "USD", null, now)))
        )
        assertEquals(listOf(
            NotificationRow("Codex", "5-Hour 75% left · Stale"),
            NotificationRow("Claude", "Reconnect required"),
            NotificationRow("Gemini", "Quota unavailable"),
            NotificationRow("OpenRouter", "Balance: USD 12.50")
        ), notificationRows(context, quotas, now))
    }

    @Test fun `expanded rows are capped and overflow counts the rest`() {
        val quotas = (1..7).map {
            connection(AiService.CODEX, "acct$it") to snapshot(AiService.CODEX, 0.50)
        }
        val (visible, overflow) = expandedRows(context, notificationRows(context, quotas, now))
        assertEquals(6, visible.size)
        assertEquals("+1 accounts · Open app", overflow)
    }

    @Test fun `six or fewer accounts show no overflow`() {
        val quotas = (1..6).map {
            connection(AiService.CODEX, "acct$it") to snapshot(AiService.CODEX, 0.50)
        }
        val (visible, overflow) = expandedRows(context, notificationRows(context, quotas, now))
        assertEquals(6, visible.size)
        assertNull(overflow)
    }

    @Test fun `oldest measurement ignores missing and future readings`() {
        val quotas = listOf(
            connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25, at = now.minusSeconds(600)),
            connection(AiService.CLAUDE) to QuotaSnapshot(),
            connection(AiService.GEMINI) to snapshot(AiService.GEMINI, 0.25, at = now.plusSeconds(600))
        )
        assertEquals(now.minusSeconds(600).toEpochMilli(), oldestMeasurement(quotas, now))
    }

    @Test fun `all missing or future readings give no timer`() {
        val quotas = listOf(
            connection(AiService.CLAUDE) to QuotaSnapshot(),
            connection(AiService.GEMINI) to snapshot(AiService.GEMINI, 0.25, at = now.plusSeconds(600))
        )
        assertNull(oldestMeasurement(quotas, now))
    }

    @Test fun `oldest measurement is stable across rebuilds`() {
        val quotas = listOf(
            connection(AiService.CODEX) to snapshot(AiService.CODEX, 0.25, at = now.minusSeconds(600)),
            connection(AiService.CLAUDE) to snapshot(AiService.CLAUDE, 0.25, at = now.minusSeconds(300))
        )
        // Rebuild (e.g. a later refresh of other accounts) must not move the header
        // timer: it derives from the source reading timestamps, not render time.
        assertEquals(oldestMeasurement(quotas, now), oldestMeasurement(quotas, now))
        assertEquals(now.minusSeconds(600).toEpochMilli(), oldestMeasurement(quotas, now))
    }
}
