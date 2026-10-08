package com.codexbar.android.core.workmanager

import com.codexbar.android.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class QuotaRefreshWorkerTest {
    @Test fun `terminal and parsing outcomes do not retry while transient accounts do`() {
        assertFalse(QuotaRefreshWorker.shouldRetry(AppError.AuthError(AiService.CODEX, true)))
        assertFalse(QuotaRefreshWorker.shouldRetry(AppError.CredentialNotFound(AiService.CODEX)))
        assertFalse(QuotaRefreshWorker.shouldRetry(AppError.ParseError("synthetic")))
        assertTrue(QuotaRefreshWorker.shouldRetry(AppError.AuthError(AiService.CODEX, false)))
        assertTrue(QuotaRefreshWorker.shouldRetry(AppError.NetworkError("synthetic")))
        assertTrue(QuotaRefreshWorker.shouldRetry(AppError.RateLimited))
        assertTrue(QuotaRefreshWorker.shouldRetry(AppError.ServiceUnavailable))
    }
}
