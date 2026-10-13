package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import javax.inject.Inject

class MiniMaxRepositoryImpl @Inject constructor(
    private val client: NativeCodexBarClient
) : QuotaRepository {
    override suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError> {
        require(session.connection.service == AiService.MINIMAX)
        val credential = session.credential as? Credential.MiniMaxCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.MINIMAX))
        val key = credential.accessToken.trim()
        // Mirrors upstream MiniMaxAPIFetchStrategy.isAvailable (rejects only the sk-api-
        // pay-as-you-go kind; unknown prefixes still reach the server). Localized guidance
        // lives in the settings key-help text; this is the runtime backstop.
        if (key.startsWith("sk-api-")) {
            return Result.Failure(AppError.AuthError(
                AiService.MINIMAX,
                isTerminal = true,
                message = "MiniMax pay-as-you-go keys (sk-api-) are not supported; use your Token Plan Subscription Key (sk-cp-)."
            ))
        }
        return client.fetchApiKey(AiService.MINIMAX, key)
    }

}
