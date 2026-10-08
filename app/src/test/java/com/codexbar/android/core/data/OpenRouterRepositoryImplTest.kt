package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class OpenRouterRepositoryImplTest {
    @Test fun `only supplied OpenRouter key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = OpenRouterRepositoryImpl(client)
        val account = AccountConnection.create(AiService.OPENROUTER)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.OPENROUTER, "synthetic-router"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.OPENROUTER, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.OpenRouterCredential("synthetic-router")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.OPENROUTER, "synthetic-router")
    }
}
