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
            val status = OfficialStatus.parse(AiService.CODEX, capture(indicator), 10000)!!
            assertEquals(indicator, status.indicator)
            assertEquals(indicator !in setOf("none", "unknown"), status.hasIncident(10000))
            assertFalse(status.hasIncident(12000))
            assertFalse(status.isRecent(9000))
        }
        assertNull(OfficialStatus.parse(AiService.CODEX, capture("none", "claude"), 10000))
        assertNull(OfficialStatus.parse(AiService.CODEX, capture("none", url = "https://example.com/"), 10000))
        assertNull(OfficialStatus.parse(AiService.CODEX, null, 10000))
        assertNull(OfficialStatus.parse(AiService.CODEX, CliProcess.Result(0, false, 1, "not json", "", false, false), 10000))
        assertNull(OfficialStatus.parse(AiService.CODEX, capture("unknown"), 10000)!!.description)
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
        val repo = OfficialStatusRepository(prefs, fetch, clock = { now })
        val first = async { repo.refresh(AiService.CODEX) }
        val second = async { repo.refresh(AiService.CODEX) }
        first.await(); second.await()
        assertEquals(1, calls)
        val restarted = OfficialStatusRepository(prefs, fetch, clock = { now })
        restarted.refresh(AiService.CODEX)
        assertEquals(1, calls)
        now += 301
        restarted.refresh(AiService.CODEX)
        assertEquals(2, calls)
        assertTrue(restarted.statuses.value.getValue(AiService.CODEX).hasIncident(now))
    }

    private fun prefsSink(): Pair<SharedPreferences, MutableMap<String, String>> {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val saved = mutableMapOf<String, String>()
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { saved[it.getArgument(0)] }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            saved[it.getArgument(0)] = it.getArgument(1); editor
        }
        return prefs to saved
    }

    @Test fun `disabled checks fetch nothing and write nothing`() = runTest {
        val (prefs, saved) = prefsSink()
        var calls = 0
        var enabled = false
        val repo = OfficialStatusRepository(prefs, { calls++; capture("major") }, { 10000 }) { enabled }
        repo.refresh(AiService.CODEX)
        assertEquals(0, calls)
        assertTrue(saved.isEmpty())
        assertEquals(0L, repo.statuses.value.getValue(AiService.CODEX).checkedAt)
        enabled = true
        repo.refresh(AiService.CODEX)
        assertEquals(1, calls)
        assertEquals("major", repo.statuses.value.getValue(AiService.CODEX).indicator)
    }

    @Test fun `disable during in-flight fetch suppresses persist and publish`() = runTest {
        val (prefs, saved) = prefsSink()
        var enabled = true
        val fetch: suspend (String) -> CliProcess.Result? = { enabled = false; capture("major") }
        val repo = OfficialStatusRepository(prefs, fetch, { 10000 }) { enabled }
        repo.refresh(AiService.CODEX)
        assertTrue(saved.isEmpty())
        assertEquals(0L, repo.statuses.value.getValue(AiService.CODEX).checkedAt)
    }

    @Test fun `re-enable keeps the existing throttle window`() = runTest {
        val (prefs, _) = prefsSink()
        var now = 10000L
        var calls = 0
        var enabled = true
        val repo = OfficialStatusRepository(prefs, { calls++; capture("none") }, { now }) { enabled }
        repo.refresh(AiService.CODEX)
        assertEquals(1, calls)
        enabled = false
        now += 100
        repo.refresh(AiService.CODEX)
        assertEquals(1, calls)
        enabled = true
        repo.refresh(AiService.CODEX)
        assertEquals("the disable window must not reset the 300s throttle", 1, calls)
        now += 300
        repo.refresh(AiService.CODEX)
        assertEquals(2, calls)
    }

    @Test fun `failed fetch preserves last good status and its checkedAt`() = runTest {
        val (prefs, saved) = prefsSink()
        var now = 10000L
        val responses = ArrayDeque<CliProcess.Result?>()
        val fetch: suspend (String) -> CliProcess.Result? = { responses.removeFirst() }
        val repo = OfficialStatusRepository(prefs, fetch, clock = { now })
        responses.add(capture("major"))
        repo.refresh(AiService.CODEX)
        responses.add(null)
        now += 301
        repo.refresh(AiService.CODEX)
        val kept = repo.statuses.value.getValue(AiService.CODEX)
        assertEquals("major", kept.indicator)
        assertEquals("a failed check must never re-stamp the last good status", 10000L, kept.checkedAt)
        assertTrue(saved.getValue("CODEX").contains("major"))
    }

    @Test fun `first failed check records the attempt without an incident`() = runTest {
        val (prefs, saved) = prefsSink()
        val repo = OfficialStatusRepository(prefs, { null }, clock = { 10000 })
        repo.refresh(AiService.CODEX)
        val attempt = repo.statuses.value.getValue(AiService.CODEX)
        assertEquals("unknown", attempt.indicator)
        assertEquals(10000L, attempt.checkedAt)
        assertFalse(attempt.hasIncident(10000))
        assertTrue(saved.isNotEmpty())
    }

    @Test fun `valid unknown publishes while failures preserve the prior status un-restamped`() = runTest {
        val (prefs, saved) = prefsSink()
        var now = 10000L
        val responses = ArrayDeque<CliProcess.Result?>()
        val fetch: suspend (String) -> CliProcess.Result? = { responses.removeFirst() }
        val repo = OfficialStatusRepository(prefs, fetch, clock = { now })
        responses.add(capture("major"))
        repo.refresh(AiService.CODEX)
        responses.add(capture("unknown"))
        now += 301
        repo.refresh(AiService.CODEX)
        val cleared = repo.statuses.value.getValue(AiService.CODEX)
        assertEquals("a valid unknown reading must replace the stale incident", "unknown", cleared.indicator)
        assertEquals(10301L, cleared.checkedAt)
        assertFalse(saved.getValue("CODEX").contains("major"))
        responses.add(null)
        now += 301
        repo.refresh(AiService.CODEX)
        val kept = repo.statuses.value.getValue(AiService.CODEX)
        assertEquals("unknown", kept.indicator)
        assertEquals("a failed check must never re-stamp the retained status", 10301L, kept.checkedAt)
    }
}
