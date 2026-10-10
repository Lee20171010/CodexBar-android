package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.AccountQuota
import com.codexbar.android.core.data.AccountQuotaCoordinator
import com.codexbar.android.core.data.OfficialStatus
import com.codexbar.android.core.data.OfficialStatusRepository
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.DisplayOptions
import com.codexbar.android.core.presentation.QuotaSnapshot
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDisplayTest {
    @Test fun `retained dashboard observes external display edits without a quota emission`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var viewModel: DashboardViewModel? = null
        var androidLog: org.mockito.MockedStatic<android.util.Log>? = null
        try {
            androidLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
            val account = AccountConnection.create(AiService.CODEX)
            val prefs = mock(EncryptedPrefsManager::class.java)
            val accounts = mock(AccountQuotaCoordinator::class.java)
            val status = mock(OfficialStatusRepository::class.java)
            val widgets = WidgetPrefsManager(MemoryPreferences().prefs)
            `when`(prefs.connections).thenReturn(MutableStateFlow(listOf(account)))
            // Isolate presentation from the constructor's network refresh.
            `when`(prefs.loadConnections()).thenReturn(emptyList())
            `when`(accounts.quotas).thenReturn(MutableStateFlow(emptyMap<String, AccountQuota>()))
            `when`(accounts.snapshot(account)).thenReturn(QuotaSnapshot())
            `when`(status.statuses).thenReturn(MutableStateFlow(emptyMap<AiService, OfficialStatus>()))
            val retained = DashboardViewModel(accounts, prefs, status, widgets, mock(Context::class.java))
            viewModel = retained
            runCurrent()
            fun display() = (retained.uiState.value as DashboardUiState.Success).cards.single().display
            assertEquals(false, display().fillUsed)

            widgets.saveDisplayOptions(account, DisplayOptions(accent = "green"), touched = setOf("accent"))
            widgets.saveGlobalDisplayOptions(DisplayOptions(fillUsed = true, workDays = 5))
            runCurrent()
            assertEquals(DisplayOptions(fillUsed = true, workDays = 5, accent = "green"), display())

            widgets.clearDisplayOptions(account)
            runCurrent()
            assertEquals(DisplayOptions(fillUsed = true, workDays = 5), display())
        } finally {
            viewModel?.viewModelScope?.cancel()
            androidLog?.close()
            Dispatchers.resetMain()
        }
    }
}
