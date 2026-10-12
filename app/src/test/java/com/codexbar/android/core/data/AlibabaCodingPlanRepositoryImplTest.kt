package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AlibabaCodingPlanRepositoryImplTest {
    @Test fun `only supplied alibaba key reaches the native provider`() = runTest {
        val client = mock(NativeCodexBarClient::class.java)
        val repository = AlibabaCodingPlanRepositoryImpl(client)
        val account = AccountConnection.create(AiService.ALIBABA_CODING_PLAN)
        assertTrue(repository.fetchQuota(CredentialSession(account, Credential.OpenCodeGoCredential("wrong-provider"))) is Result.Failure)
        verifyNoInteractions(client)
        `when`(client.fetchApiKey(AiService.ALIBABA_CODING_PLAN, "synthetic-alibaba"))
            .thenReturn(Result.Failure(AppError.AuthError(AiService.ALIBABA_CODING_PLAN, true)))
        val result = repository.fetchQuota(CredentialSession(account, Credential.AlibabaCodingPlanCredential("synthetic-alibaba")))
        assertTrue(result is Result.Failure)
        verify(client).fetchApiKey(AiService.ALIBABA_CODING_PLAN, "synthetic-alibaba")
    }
}
