package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class HyperRepositoryImplTest {
    @Test fun `only supplied hyper key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = HyperRepositoryImpl(client)
        val account = AccountConnection.create(AiService.HYPER)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.HYPER, "synthetic-hyper"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.HYPER, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.HyperCredential("synthetic-hyper")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.HYPER, "synthetic-hyper")
    }
}
