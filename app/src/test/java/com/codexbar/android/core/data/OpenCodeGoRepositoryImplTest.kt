package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.repository.CredentialSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class OpenCodeGoRepositoryImplTest {
    @Test fun missingKeyDoesNotStartNativeClient() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val session = CredentialSession(AccountConnection.create(AiService.OPENCODE_GO), null)
        val result = OpenCodeGoRepositoryImpl(client).fetchQuota(session) as Result.Failure
        assertEquals(AppError.CredentialNotFound(AiService.OPENCODE_GO), result.error)
        verifyNoInteractions(client)
    }

    @Test fun forwardsSavedKeyAndPreservesItOnAuthenticationFailure() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val session = CredentialSession(AccountConnection.create(AiService.OPENCODE_GO), Credential.OpenCodeGoCredential("synthetic-key"))
        `when`(client.fetchOpenCodeGo("synthetic-key")).thenReturn(Result.Failure(AppError.AuthError(AiService.OPENCODE_GO, true)))
        val result = OpenCodeGoRepositoryImpl(client).fetchQuota(session) as Result.Failure
        assertTrue(result.error is AppError.AuthError)
        verify(client).fetchOpenCodeGo("synthetic-key")
        assertEquals("synthetic-key", session.credential?.accessToken)
    }
}
