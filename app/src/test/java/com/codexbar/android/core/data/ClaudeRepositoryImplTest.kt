package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.claude.ClaudeCompanionAuthenticationException
import com.codexbar.android.core.network.claude.ClaudeCompanionClient
import com.codexbar.android.core.network.claude.ClaudeCompanionSnapshot
import com.codexbar.android.core.network.claude.ClaudeCompanionWindow
import com.codexbar.android.core.network.companion.LocalCompanionLocator
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ClaudeRepositoryImplTest {
    private val prefsManager = mock(EncryptedPrefsManager::class.java)
    private val credential = Credential.ClaudeCompanionCredential(
        host = "127.0.0.1",
        port = 43823,
        companionId = "5b017391-6dc4-4ab7-b0ad-2255dada62d7",
        sharedKeyBase64Url = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    )
    private val noCandidates = locatorReturning(emptyList())

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

        val result = ClaudeRepositoryImpl(client, prefsManager, noCandidates).fetchQuota()

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

        val result = ClaudeRepositoryImpl(clientReturning(null), prefsManager, noCandidates).fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `validateCredential rejects non companion credentials`() = runTest {
        val repository = ClaudeRepositoryImpl(clientReturning(null), prefsManager, noCandidates)

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

        val result = ClaudeRepositoryImpl(client, prefsManager, noCandidates).fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    @Test
    fun `an unreachable companion is found again on its new address`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        val movedTo = "192.168.1.42"
        val client = clientReachableAt(movedTo)
        `when`(prefsManager.updateClaudeCompanionHostIfCurrent(credential, movedTo))
            .thenReturn(true)

        val result = ClaudeRepositoryImpl(
            client,
            prefsManager,
            locatorReturning(listOf("192.168.1.41", movedTo))
        ).fetchQuota()

        assertTrue(result is Result.Success)
        verify(prefsManager).updateClaudeCompanionHostIfCurrent(credential, movedTo)
    }

    @Test
    fun `disconnect while relocation is in flight does not restore the pairing`() = runTest {
        assertPairingChangeDuringRelocation(null)
    }

    @Test
    fun `re-pair while relocation is in flight does not overwrite the new pairing`() = runTest {
        assertPairingChangeDuringRelocation(credential.copy(companionId = "new-companion"))
    }

    private suspend fun assertPairingChangeDuringRelocation(
        replacement: Credential.ClaudeCompanionCredential?
    ) = kotlinx.coroutines.coroutineScope {
        var storedCredential = credential as Credential.ClaudeCompanionCredential?
        val movedTo = "192.168.1.42"
        val candidateStarted = CompletableDeferred<Unit>()
        val resumeCandidate = CompletableDeferred<Unit>()
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenAnswer { storedCredential }
        `when`(prefsManager.updateClaudeCompanionHostIfCurrent(credential, movedTo))
            .thenAnswer {
                if (storedCredential == credential) {
                    storedCredential = credential.copy(host = movedTo)
                    true
                } else {
                    false
                }
            }
        val client = object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                if (credential.host != movedTo) throw IOException("companion unavailable")
                candidateStarted.complete(Unit)
                resumeCandidate.await()
                return clientReachableAt(movedTo).fetchSnapshot(credential, now)
            }
        }
        val pending = async {
            ClaudeRepositoryImpl(client, prefsManager, locatorReturning(listOf(movedTo)))
                .fetchQuota()
        }
        candidateStarted.await()
        storedCredential = replacement
        resumeCandidate.complete(Unit)

        assertTrue(pending.await() is Result.Failure)
        assertEquals(replacement, storedCredential)
        verify(prefsManager).updateClaudeCompanionHostIfCurrent(credential, movedTo)
        verify(prefsManager, never())
            .saveCredential(AiService.CLAUDE, credential.copy(host = movedTo))
    }

    @Test
    fun `a candidate that cannot authenticate never replaces the pairing`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        val client = object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                if (credential.host == "192.168.1.9") {
                    throw ClaudeCompanionAuthenticationException("not our companion")
                }
                throw IOException("companion unavailable")
            }
        }

        val result = ClaudeRepositoryImpl(
            client,
            prefsManager,
            locatorReturning(listOf("192.168.1.9"))
        ).fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.NetworkError)
        verify(prefsManager, never())
            .updateClaudeCompanionHostIfCurrent(credential, "192.168.1.9")
    }

    @Test
    fun `terminal pairing failures never start a scan`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        var scans = 0
        val locator = object : LocalCompanionLocator() {
            override suspend fun reachableHosts(
                port: Int,
                previousHost: String?,
                limit: Int
            ): List<String> {
                scans++
                return emptyList()
            }
        }
        val client = object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                throw ClaudeCompanionAuthenticationException("invalid pairing")
            }
        }

        ClaudeRepositoryImpl(client, prefsManager, locator).fetchQuota()

        assertEquals(0, scans)
    }

    @Test
    fun `a switched off companion is not rescanned on every refresh`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(credential)
        var scans = 0
        val locator = object : LocalCompanionLocator() {
            override suspend fun reachableHosts(
                port: Int,
                previousHost: String?,
                limit: Int
            ): List<String> {
                scans++
                return emptyList()
            }
        }
        val repository = ClaudeRepositoryImpl(
            clientReturning(null),
            prefsManager,
            locator,
            nowMillis = { 1_000L }
        )

        repository.fetchQuota()
        repository.fetchQuota()
        repository.fetchQuota()

        assertEquals(1, scans)
    }

    private fun locatorReturning(hosts: List<String>): LocalCompanionLocator {
        return object : LocalCompanionLocator() {
            override suspend fun reachableHosts(
                port: Int,
                previousHost: String?,
                limit: Int
            ): List<String> = hosts
        }
    }

    private fun clientReachableAt(host: String): ClaudeCompanionClient {
        return object : ClaudeCompanionClient(Json) {
            override suspend fun fetchSnapshot(
                credential: Credential.ClaudeCompanionCredential,
                now: Instant
            ): ClaudeCompanionSnapshot {
                if (credential.host != host) throw IOException("companion unavailable")
                return ClaudeCompanionSnapshot(
                    schemaVersion = 1,
                    source = "claude-cli-terminal",
                    generatedAtEpochSeconds = 1_750_000_000L,
                    cliVersion = "official-cli",
                    tier = "Max 5x",
                    windows = listOf(
                        ClaudeCompanionWindow(
                            label = "5-Hour",
                            usedFraction = 0.2,
                            resetsAtEpochSeconds = null
                        )
                    )
                )
            }
        }
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
