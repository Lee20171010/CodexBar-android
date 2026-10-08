package com.codexbar.android.core.domain.repository

import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result

interface QuotaRepository {
    suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError>
}
