package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ZaiRepositoryImplTest {
    @Test fun `only supplied z ai key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = ZaiRepositoryImpl(client)
        val account = AccountConnection.create(AiService.ZAI)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.ZAI, "synthetic-zai"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.ZAI, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.ZaiCredential("synthetic-zai")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.ZAI, "synthetic-zai")
    }
}
