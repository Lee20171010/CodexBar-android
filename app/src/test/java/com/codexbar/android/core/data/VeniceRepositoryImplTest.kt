package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class VeniceRepositoryImplTest {
    @Test fun `only supplied Venice key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = VeniceRepositoryImpl(client)
        val account = AccountConnection.create(AiService.VENICE)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.VENICE, "synthetic-venice"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.VENICE, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.VeniceCredential("synthetic-venice")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.VENICE, "synthetic-venice")
    }
}
