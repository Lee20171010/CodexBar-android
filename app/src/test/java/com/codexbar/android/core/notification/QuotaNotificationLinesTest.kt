package com.codexbar.android.core.notification

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.presentation.QuotaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.time.Instant

class QuotaNotificationLinesTest {

    private val now = Instant.now()

    private val context: Context = Mockito.mock(Context::class.java).also {
        Mockito.`when`(it.getString(R.string.measured_now)).thenReturn("just now")
        Mockito.`when`(it.getString(R.string.quota_unavailable)).thenReturn("unavailable")
        Mockito.`when`(it.getString(R.string.percent_left, 75)).thenReturn("75% left")
        Mockito.`when`(it.getString(R.string.percent_left, 40)).thenReturn("40% left")
    }

    private fun snapshot(service: AiService, utilization: Double) = QuotaSnapshot(
        QuotaInfo(service, listOf(
            UsageWindow("5-Hour", utilization, now.plusSeconds(3600), "primary", durationSeconds = 300),
            UsageWindow("Extra", 0.99, null, "supp", supplemental = true)
        ), null, fetchedAt = now, source = "native-oauth")
    )

    @Test fun `no accounts produce no lines`() {
        assertEquals(emptyList<String>(), quotaSummaryLines(context, emptyList()))
    }

    @Test fun `single account line carries name window percent and age`() {
        val lines = quotaSummaryLines(context,
            listOf(AccountConnection.create(AiService.CODEX) to snapshot(AiService.CODEX, 0.25)))
        assertEquals(listOf("Codex · 5-Hour 75% left · just now"), lines)
    }

    @Test fun `every connection gets its own line in order`() {
        val quotas = listOf(
            AccountConnection.create(AiService.CODEX) to snapshot(AiService.CODEX, 0.25),
            AccountConnection.create(AiService.CLAUDE) to snapshot(AiService.CLAUDE, 0.60),
            AccountConnection.create(AiService.GEMINI, "Gemini work") to snapshot(AiService.GEMINI, 0.25)
        )
        assertEquals(listOf(
            "Codex · 5-Hour 75% left · just now",
            "Claude · 5-Hour 40% left · just now",
            "Gemini work · 5-Hour 75% left · just now"
        ), quotaSummaryLines(context, quotas))
    }

    @Test fun `account without a usable reading still gets a line`() {
        val lines = quotaSummaryLines(context,
            listOf(AccountConnection.create(AiService.CODEX) to QuotaSnapshot()))
        assertEquals(listOf("Codex · unavailable"), lines)
    }
}
