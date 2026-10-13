package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class MiniMaxRepositoryImplTest {
    @Test fun `only minimax keys reach the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = MiniMaxRepositoryImpl(client)
        val account = AccountConnection.create(AiService.MINIMAX)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.MINIMAX, "sk-cp-synthetic"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.MINIMAX, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.MiniMaxCredential(" sk-cp-synthetic ")))
        assertTrue(result is Result.Failure)
        // Trimmed consistently before dispatch.
        verify(client).fetchApiKey(AiService.MINIMAX, "sk-cp-synthetic")
    }

    @Test fun `known pay-as-you-go key is rejected before the client`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = MiniMaxRepositoryImpl(client)
        val account = AccountConnection.create(AiService.MINIMAX)
        val result = repository.fetchQuota(CredentialSession(account, Credential.MiniMaxCredential("sk-api-synthetic")))
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
        assertTrue((error as AppError.AuthError).isTerminal)
        assertTrue(error.message.contains("sk-cp"))
        verifyNoInteractions(client)
    }

    @Test fun `unknown key prefix passes through to the server`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = MiniMaxRepositoryImpl(client)
        val account = AccountConnection.create(AiService.MINIMAX)
        `when`(client.fetchApiKey(AiService.MINIMAX, "legacy-synthetic"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.MINIMAX, true)))
        repository.fetchQuota(CredentialSession(account, Credential.MiniMaxCredential("legacy-synthetic")))
        verify(client).fetchApiKey(AiService.MINIMAX, "legacy-synthetic")
    }
}
