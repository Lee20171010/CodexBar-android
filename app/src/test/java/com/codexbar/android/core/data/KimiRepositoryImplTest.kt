package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class KimiRepositoryImplTest {
    @Test fun `only supplied kimi key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = KimiRepositoryImpl(client)
        val account = AccountConnection.create(AiService.KIMI)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.KIMI, "synthetic-kimi"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.KIMI, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.KimiCredential("synthetic-kimi")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.KIMI, "synthetic-kimi")
    }
}
