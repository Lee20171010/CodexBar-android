package com.codexbar.android.core.security

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionHealthStoreTest {

    @Test
    fun `terminal authentication failure requires reauthentication`() {
        val error = AppError.AuthError(AiService.CODEX, isTerminal = true)

        assertEquals(ConnectionHealth.NEEDS_REAUTHENTICATION, error.toConnectionHealth())
    }

    @Test
    fun `temporary network and authentication failures remain offline`() {
        assertEquals(
            ConnectionHealth.OFFLINE,
            AppError.NetworkError("offline").toConnectionHealth()
        )
        assertEquals(
            ConnectionHealth.OFFLINE,
            AppError.AuthError(AiService.CODEX, isTerminal = false).toConnectionHealth()
        )
    }

    @Test
    fun `missing credential clears remembered connection health`() {
        assertEquals(
            ConnectionHealth.UNKNOWN,
            AppError.CredentialNotFound(AiService.CLAUDE).toConnectionHealth()
        )
    }
}
