package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import com.codexbar.android.core.security.EncryptedPrefsManager
import javax.inject.Inject

class OpenCodeGoRepositoryImpl @Inject constructor(
    private val client: NativeCodexBarClient,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository {
    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> = fetchQuota(
        CredentialSession(AccountConnection.create(AiService.OPENCODE_GO), prefsManager.loadCredential(AiService.OPENCODE_GO))
    )

    override suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError> {
        require(session.connection.service == AiService.OPENCODE_GO)
        val credential = session.credential as? Credential.OpenCodeGoCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.OPENCODE_GO))
        return client.fetchOpenCodeGo(credential.accessToken)
    }

    override suspend fun validateCredential(): Result<Unit, AppError> = when (val result = fetchQuota()) {
        is Result.Success -> Result.Success(Unit)
        is Result.Failure -> Result.Failure(result.error)
    }
}
