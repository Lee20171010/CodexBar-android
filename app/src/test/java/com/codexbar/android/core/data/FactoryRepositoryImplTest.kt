package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class FactoryRepositoryImplTest {
    @Test fun `only supplied factory key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = FactoryRepositoryImpl(client)
        val account = AccountConnection.create(AiService.FACTORY)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.FACTORY, "synthetic-factory"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.FACTORY, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.FactoryCredential("synthetic-factory")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.FACTORY, "synthetic-factory")
    }
}
