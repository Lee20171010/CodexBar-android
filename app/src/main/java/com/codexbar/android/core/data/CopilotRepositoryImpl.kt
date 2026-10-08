package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.nativecli.NativeCodexBarClient

class CopilotRepositoryImpl(private val client: NativeCodexBarClient) : QuotaRepository {
    override suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError> {
        require(session.connection.service == AiService.COPILOT)
        val credential = session.credential as? Credential.CopilotCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.COPILOT))
        return client.fetchApiKey(AiService.COPILOT, credential.accessToken)
    }
}
