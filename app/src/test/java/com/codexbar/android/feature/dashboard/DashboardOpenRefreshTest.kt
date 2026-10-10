package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuota
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.data.OfficialStatus
import com.codexbar.android.core.data.OfficialStatusRepository
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.presentation.QuotaFailure
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

/** Open-triggered refresh semantics mirroring Mac MenuOpenRefreshPlan (default OFF). */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardOpenRefreshTest {
    private class Fixture(
        scheduler: TestCoroutineScheduler,
        connections: List<AccountConnection>,
        snapshots: Map<AccountConnection, QuotaSnapshot>,
        refreshOnOpen: Boolean = false
    ) {
        val accounts = mock(AccountQuotaCoordinator::class.java)
        val status = mock(OfficialStatusRepository::class.java)
        val quotaCalls = mutableListOf<AccountConnection>()
        val statusCalls = mutableListOf<AiService>()
        val viewModel: DashboardViewModel

        init {
            val prefs = mock(EncryptedPrefsManager::class.java)
            runBlocking {
                `when`(prefs.connections).thenReturn(MutableStateFlow(connections))
                `when`(prefs.loadConnections()).thenReturn(connections)
                `when`(prefs.getRefreshOnOpen()).thenReturn(refreshOnOpen)
                `when`(prefs.statusChecksEnabled).thenReturn(MutableStateFlow(true))
                `when`(accounts.quotas).thenReturn(MutableStateFlow(emptyMap<String, AccountQuota>()))
                `when`(status.statuses).thenReturn(MutableStateFlow(emptyMap<AiService, OfficialStatus>()))
                snapshots.forEach { (account, snapshot) -> `when`(accounts.snapshot(account)).thenReturn(snapshot) }
                connections.forEach { account ->
                    doAnswer { invocation -> quotaCalls += invocation.getArgument<AccountConnection>(0); null }
                        .`when`(accounts).refresh(account)
                }
                connections.map { it.service }.distinct().forEach { service ->
                    doAnswer { invocation -> statusCalls += invocation.getArgument<AiService>(0); null }
                        .`when`(status).refresh(service)
                }
            }
            Dispatchers.setMain(StandardTestDispatcher(scheduler))
            viewModel = DashboardViewModel(accounts, prefs, status,
                WidgetPrefsManager(MemoryPreferences().prefs), mock(Context::class.java))
        }
    }

    private fun healthyQuota(service: AiService = AiService.CODEX) = QuotaInfo(
        service, listOf(UsageWindow("Session", 0.5, null)), null,
        fetchedAt = Instant.now().minusSeconds(7200))

    private fun healthy(service: AiService = AiService.CODEX) =
        QuotaSnapshot(healthyQuota(service), Instant.now())

    @Test fun `constructor renders cache without fetching and first open fetches once`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(account), mapOf(account to healthy()))
            runCurrent()
            assertEquals("constructor must render cached quota without any fetch", 0, fixture.quotaCalls.size)
            assertEquals(1, (fixture.viewModel.uiState.value as DashboardUiState.Success).cards.single().windows.size)

            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals(listOf(account), fixture.quotaCalls)
            assertEquals(listOf(AiService.CODEX), fixture.statusCalls)

            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("aged healthy quota must not refetch while refresh-on-open is off", 1, fixture.quotaCalls.size)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `off open recovers only failed or missing accounts sequentially`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val failed = AccountConnection.create(AiService.CODEX)
            val missing = AccountConnection.create(AiService.CLAUDE)
            val healthyAccount = AccountConnection.create(AiService.DEEPSEEK)
            val snapshots = mapOf(
                failed to QuotaSnapshot(healthyQuota(), Instant.now(), QuotaFailure.NETWORK),
                missing to QuotaSnapshot(),
                healthyAccount to healthy()
            )
            val accounts = listOf(failed, missing, healthyAccount)
            fixture = Fixture(testScheduler, accounts, snapshots)
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("cold first open fetches every eligible account", 3, fixture.quotaCalls.size)
            fixture.quotaCalls.clear()

            // Gate the failed account's fetch: a sequential recovery must not start the next
            // account until this one completes; a concurrent pass would call both immediately.
            var gated: Continuation<Any?>? = null
            runBlocking {
                doAnswer { invocation ->
                    fixture!!.quotaCalls += invocation.getArgument<AccountConnection>(0)
                    @Suppress("UNCHECKED_CAST")
                    val continuation = invocation.rawArguments.last() as Continuation<Any?>
                    gated = continuation
                    COROUTINE_SUSPENDED
                }.`when`(fixture.accounts).refresh(failed)
            }
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("sequential recovery must wait for the first fetch to complete",
                listOf(failed), fixture.quotaCalls)
            gated!!.resume(null)
            runCurrent()
            assertEquals("only the failed and missing accounts recover, in order",
                listOf(failed, missing), fixture.quotaCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `terminal reconnect accounts skip automatic fetch but manual refresh fetches`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(account),
                mapOf(account to QuotaSnapshot(null, Instant.now(), QuotaFailure.AUTH)))
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("terminal AUTH must never be auto-fetched, like the periodic worker",
                emptyList<AccountConnection>(), fixture.quotaCalls)
            assertEquals("status checks stay independent of quota eligibility",
                listOf(AiService.CODEX), fixture.statusCalls)

            fixture.viewModel.refresh()
            runCurrent()
            assertEquals(listOf(account), fixture.quotaCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `on open refreshes healthy accounts on every open`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(account), mapOf(account to healthy()), refreshOnOpen = true)
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals(2, fixture.quotaCalls.size)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `open coalesces while a refresh is already running`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(account), mapOf(account to healthy()))
            runCurrent()
            fixture.viewModel.refresh()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("open during an in-flight refresh must not start a second pass",
                1, fixture.quotaCalls.size)
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals("a coalesced open consumes the cold first-open allowance",
                1, fixture.quotaCalls.size)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `off open with nothing eligible still dispatches status once per service`() = runTest {
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        var fixture: Fixture? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            fixture = Fixture(testScheduler, listOf(account), mapOf(account to healthy()))
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            fixture.viewModel.onDashboardOpen()
            runCurrent()
            assertEquals(1, fixture.quotaCalls.size)
            assertEquals("second open has no quota work but must still check status",
                listOf(AiService.CODEX, AiService.CODEX), fixture.statusCalls)
        } finally {
            fixture?.viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }
}
