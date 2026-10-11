package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuota
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.data.OfficialStatus
import com.codexbar.android.core.data.OfficialStatusRepository
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaFailure
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

/** Segmented/stacked display selection: pointer fallback, scoped refresh, no dropped switches. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardMultiAccountTest {
    private class Fixture(
        scheduler: TestCoroutineScheduler,
        connections: List<AccountConnection>,
        segmented: Boolean = true,
        pointers: Map<String, String> = emptyMap(),
        current: Set<AccountConnection> = connections.toSet(),
        snapshots: Map<AccountConnection, QuotaSnapshot> = emptyMap()
    ) {
        val accounts = mock(AccountQuotaCoordinator::class.java)
        val status = mock(OfficialStatusRepository::class.java)
        val quotaCalls = mutableListOf<AccountConnection>()
        val displayed = MutableStateFlow(pointers)
        val segmentedFlow = MutableStateFlow(segmented)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val viewModel: DashboardViewModel

        init {
            runBlocking {
                `when`(prefs.connections).thenReturn(MutableStateFlow(connections))
                `when`(prefs.loadConnections()).thenReturn(connections)
                `when`(prefs.statusChecksEnabled).thenReturn(MutableStateFlow(true))
                `when`(prefs.multiAccountSegmented).thenReturn(segmentedFlow)
                `when`(prefs.displayedConnections).thenReturn(displayed)
                current.forEach { `when`(prefs.isCurrent(it)).thenReturn(true) }
                connections.forEach { chosen ->
                    doAnswer {
                        if (chosen !in current) return@doAnswer false
                        displayed.value = displayed.value + (chosen.service.name to chosen.id)
                        true
                    }.`when`(prefs).setDisplayedConnection(chosen)
                }
                `when`(accounts.quotas).thenReturn(MutableStateFlow(emptyMap<String, AccountQuota>()))
                `when`(status.statuses).thenReturn(MutableStateFlow(emptyMap<AiService, OfficialStatus>()))
                connections.map { it.service }.distinct().forEach { service ->
                    doAnswer { Unit }.`when`(status).refresh(service)
                }
                connections.forEach { account ->
                    `when`(accounts.snapshot(account)).thenReturn(snapshots[account] ?: QuotaSnapshot())
                    doAnswer { invocation -> quotaCalls += invocation.getArgument<AccountConnection>(0); null }
                        .`when`(accounts).refresh(account)
                }
            }
            Dispatchers.setMain(StandardTestDispatcher(scheduler))
            viewModel = DashboardViewModel(accounts, prefs, status,
                WidgetPrefsManager(MemoryPreferences().prefs), mock(Context::class.java))
        }
    }

    private fun success(fixture: Fixture) = fixture.viewModel.uiState.value as DashboardUiState.Success

    @Test fun `segmented keeps all cards in state and marks only displayed ids`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            val codex = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(a1, a2, codex))
            runCurrent()
            val state = success(fixture!!)
            assertEquals("all cards stay in state; filtering is UI-only", 3, state.cards.size)
            assertEquals(setOf(a1.id, codex.id), state.displayedIds)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `pointer wins then legacy default account then first added`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var first: Fixture? = null
        var second: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val legacy = AccountConnection("CODEX", AiService.CODEX, "Codex", UUID.randomUUID().toString())
            val other = AccountConnection.create(AiService.CODEX)
            first = Fixture(testScheduler, listOf(other, legacy))
            runCurrent()
            assertEquals("no pointer prefers the legacy default account",
                setOf(legacy.id), success(first!!).displayedIds)
            first!!.viewModel.viewModelScope.cancel()
            second = Fixture(testScheduler, listOf(other, legacy), pointers = mapOf("CODEX" to other.id))
            runCurrent()
            assertEquals("stored pointer wins over the legacy default",
                setOf(other.id), success(second!!).displayedIds)
        } finally {
            first?.viewModel?.viewModelScope?.cancel()
            second?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `chip switch persists pointer and refreshes only that account`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2))
            runCurrent()
            fixture!!.viewModel.setDisplayed(a2)
            runCurrent()
            assertEquals(mapOf("CLAUDE" to a2.id), fixture!!.displayed.value)
            assertEquals("scoped refresh touches only the switched-to account",
                listOf(a2), fixture!!.quotaCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `same chip is a no-op and stale selection cannot be stored`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2), pointers = mapOf("CLAUDE" to a2.id))
            runCurrent()
            fixture!!.viewModel.setDisplayed(a2)
            runCurrent()
            assertEquals("clicking the selected chip must not refetch", emptyList<AccountConnection>(), fixture!!.quotaCalls)
            val gone = AccountConnection.create(AiService.CLAUDE)
            fixture!!.viewModel.setDisplayed(gone)
            runCurrent()
            assertEquals("a removed account must be ignored", mapOf("CLAUDE" to a2.id), fixture!!.displayed.value)
            assertEquals(emptyList<AccountConnection>(), fixture!!.quotaCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `terminal reconnect account switches display without auto refresh`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2),
                snapshots = mapOf(a2 to QuotaSnapshot(null, Instant.now(), QuotaFailure.AUTH)))
            runCurrent()
            fixture!!.viewModel.setDisplayed(a2)
            runCurrent()
            assertEquals(mapOf("CLAUDE" to a2.id), fixture!!.displayed.value)
            assertEquals("terminal reconnect is never auto-fetched, like the worker path",
                emptyList<AccountConnection>(), fixture!!.quotaCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `rapid consecutive switches both persist and refresh`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2))
            runCurrent()
            fixture!!.viewModel.setDisplayed(a2)
            fixture!!.viewModel.setDisplayed(a1)
            runCurrent()
            assertEquals(listOf(a2, a1), fixture!!.quotaCalls)
            assertEquals(mapOf("CLAUDE" to a1.id), fixture!!.displayed.value)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `chip switch during a suspended full refresh is not dropped`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2))
            runCurrent()
            var gated: kotlin.coroutines.Continuation<Any?>? = null
            runBlocking {
                doAnswer { invocation ->
                    gated = invocation.rawArguments.last() as kotlin.coroutines.Continuation<Any?>
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }.`when`(fixture!!.accounts).refresh(a1)
            }
            fixture!!.viewModel.refresh()
            runCurrent()
            assertTrue("the full refresh must be suspended mid-fetch", gated != null)
            fixture!!.viewModel.setDisplayed(a2)
            runCurrent()
            assertEquals("the switch persists while refreshJob is still in flight",
                mapOf("CLAUDE" to a2.id), fixture!!.displayed.value)
            assertEquals("the scoped switch refresh runs without waiting on refreshJob",
                2, fixture!!.quotaCalls.count { it == a2 })
            gated!!.resume(null)
            runCurrent()
            assertTrue(fixture!!.viewModel.uiState.value is DashboardUiState.Success)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `setter rejection race stores nothing and refreshes nothing`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2), pointers = mapOf("CLAUDE" to a1.id))
            runCurrent()
            runBlocking {
                doAnswer { false }.`when`(fixture!!.prefs).setDisplayedConnection(a2)
            }
            fixture!!.viewModel.setDisplayed(a2)
            runCurrent()
            assertEquals("a rejected switch must not move the pointer",
                mapOf("CLAUDE" to a1.id), fixture!!.displayed.value)
            assertEquals(emptyList<AccountConnection>(), fixture!!.quotaCalls)
            assertTrue("a stale selection is silently ignored, not an error state",
                fixture!!.viewModel.uiState.value is DashboardUiState.Success)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `stored pointer to a removed account falls back to first added`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2), pointers = mapOf("CLAUDE" to "removed-id"))
            runCurrent()
            assertEquals(setOf(a1.id), success(fixture!!).displayedIds)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `layout preference toggles the live state`() = runTest {
        var log: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            log = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val a1 = AccountConnection.create(AiService.CLAUDE)
            val a2 = AccountConnection.create(AiService.CLAUDE)
            fixture = Fixture(testScheduler, listOf(a1, a2))
            runCurrent()
            assertTrue(success(fixture!!).segmented)
            fixture!!.segmentedFlow.value = false
            runCurrent()
            assertFalse(success(fixture!!).segmented)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            log?.close()
            Dispatchers.resetMain()
        }
    }
}
