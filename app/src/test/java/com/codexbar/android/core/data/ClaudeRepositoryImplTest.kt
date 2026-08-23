package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.claude.ClaudeCompanionAuthenticationException
import com.codexbar.android.core.network.claude.ClaudeCompanionClient
import com.codexbar.android.core.network.claude.ClaudeCompanionSnapshot
import com.codexbar.android.core.network.claude.ClaudeCompanionWindow
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ClaudeRepositoryImplTest {
    private val prefsManager = mock(EncryptedPrefsManager::class.java)
    private val credential = Credential.ClaudeCompanionCredential(
        host = "127.0.0.1",
        port = 43823,
        companionId = "5b017391-6dc4-4ab7-b0ad-2255dada62d7",
        sharedKeyBase64Url = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    )

    @Test
    fun `fetchQuota maps sanitized official CLI companion snapshot`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        val generatedAt = Instant.ofEpochSecond(1_750_000_000L)
        val client = clientReturning(
            ClaudeCompanionSnapshot(
                schemaVersion = 1,
                source = "claude-cli-terminal",
                generatedAtEpochSeconds = generatedAt.epochSecond,
                cliVersion = "official-cli",
                tier = "Max 5x",
                windows = listOf(
                    ClaudeCompanionWindow(
                        label = "5-Hour",
                        usedFraction = 0.37,
                        resetsAtEpochSeconds = generatedAt.plusSeconds(5_400).epochSecond
                    )
                )
            )
        )

        val result = ClaudeRepositoryImpl(client, prefsManager).fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals("Max 5x", quota.tier)
        assertEquals(0.37, quota.windows.single().utilization, 0.001)
        assertEquals(18_000L, quota.windows.single().windowDurationSeconds)
        assertEquals(generatedAt, quota.fetchedAt)
    }

    @Test
    fun `fetchQuota requires a companion pairing`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(null)

        val result = ClaudeRepositoryImpl(clientReturning(null), prefsManager).fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `validateCredential rejects non companion credentials`() = runTest {
        val repository = ClaudeRepositoryImpl(clientReturning(null), prefsManager)

        val result = repository.validateCredential(Credential.CopilotCredential("not-claude"))

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    @Test
    fun `companion authentication errors require a new pairing`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        val client = object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                throw ClaudeCompanionAuthenticationException("invalid pairing")
            }
        }

        val result = ClaudeRepositoryImpl(client, prefsManager).fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    private fun clientReturning(snapshot: ClaudeCompanionSnapshot?): ClaudeCompanionClient {
        return object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                return snapshot ?: throw IOException("companion unavailable")
            }
        }
    }
}
