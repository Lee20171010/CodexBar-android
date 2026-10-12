package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AmpRepositoryImplTest {
    @Test fun `only supplied Amp key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = AmpRepositoryImpl(client)
        val account = AccountConnection.create(AiService.AMP)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.AMP, "synthetic-amp"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.AMP, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.AmpCredential("synthetic-amp")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.AMP, "synthetic-amp")
    }
}
