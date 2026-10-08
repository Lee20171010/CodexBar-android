package com.codexbar.android.core.data

import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.nativecli.CliProcess
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class OfficialStatusTest {
    private fun capture(indicator: String, provider: String = "codex", url: String = "https://status.openai.com/") =
        CliProcess.Result(0, false, 1, """[{"provider":"$provider","status":{"indicator":"$indicator","description":"Service notice","url":"$url","updatedAt":"2026-10-08T00:00:00Z"}}]""", "", false, false)

    @Test fun `official incidents stay distinct from unknown and stale checks`() {
        for (indicator in listOf("none", "minor", "major", "critical", "maintenance", "unknown")) {
            val status = OfficialStatus.parse(AiService.CODEX, capture(indicator), 10000)
            assertEquals(indicator, status.indicator)
            assertEquals(indicator !in setOf("none", "unknown"), status.hasIncident(10000))
            assertFalse(status.hasIncident(12000))
            assertFalse(status.isRecent(9000))
        }
        assertEquals("unknown", OfficialStatus.parse(AiService.CODEX, capture("none", "claude"), 10000).indicator)
        assertEquals("unknown", OfficialStatus.parse(AiService.CODEX, capture("none", url = "https://example.com/"), 10000).indicator)
        assertEquals("unknown", OfficialStatus.parse(AiService.CODEX, null, 10000).indicator)
        assertNull(OfficialStatus.parse(AiService.CODEX, capture("unknown"), 10000).description)
        assertNull(OfficialStatus.provider(AiService.OPENCODE_GO))
    }

    @Test fun `concurrent accounts share throttled status and restart cache`() = runTest {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val saved = mutableMapOf<String, String>()
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { saved[it.getArgument(0)] }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            saved[it.getArgument(0)] = it.getArgument(1); editor
        }
        var now = 10000L
        var calls = 0
        val fetch: suspend (String) -> CliProcess.Result? = { calls++; delay(100); capture("major") }
        val repo = OfficialStatusRepository(prefs, fetch) { now }
        val first = async { repo.refresh(AiService.CODEX) }
        val second = async { repo.refresh(AiService.CODEX) }
        first.await(); second.await()
        assertEquals(1, calls)
        val restarted = OfficialStatusRepository(prefs, fetch) { now }
        restarted.refresh(AiService.CODEX)
        assertEquals(1, calls)
        now += 301
        restarted.refresh(AiService.CODEX)
        assertEquals(2, calls)
        assertTrue(restarted.statuses.value.getValue(AiService.CODEX).hasIncident(now))
    }
}
