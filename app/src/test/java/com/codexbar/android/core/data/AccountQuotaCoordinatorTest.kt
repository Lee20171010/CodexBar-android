package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.MemoryPreferences
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountQuotaCoordinatorTest {
    private val prefs = EncryptedPrefsManager(MemoryPreferences().prefs)
    private val cache = WidgetPrefsManager(MemoryPreferences().prefs)
    private var fetch: suspend (CredentialSession) -> Result<QuotaInfo, AppError> = { success(it.connection) }
    private val repository = object : QuotaRepository {
        override suspend fun fetchQuota(session: CredentialSession) = fetch(session)
    }
    private val coordinator = AccountQuotaCoordinator(repository, repository, repository, repository, repository,
        prefs, cache, mock(QuotaNotificationService::class.java))

    @Test fun `failed draft never replaces saved account or publishes draft rotations`() = runTest {
        val saved = save("first")
        fetch = { it.replace(key("rotated-draft")); Result.Failure(AppError.RateLimited) }
        assertTrue(coordinator.validateAndSave(saved.reconnect(), key("draft"), saved) is Result.Failure)
        assertEquals("first", prefs.loadCredential(saved)?.accessToken)
        assertTrue(coordinator.quotas.value.isEmpty())
        assertNull(cache.getCachedQuota(saved))
    }

    @Test fun `same-account overlap reloads rotated credentials and siblings remain independent`() = runTest {
        val first = save("first")
        val sibling = save("sibling")
        val gate = CompletableDeferred<Unit>()
        val seen = mutableListOf<String>()
        fetch = {
            val token = it.credential!!.accessToken
            seen += token
            if (token == "first") { gate.await(); it.replace(key("rotated")) }
            success(it.connection)
        }
        val foreground = async { coordinator.refresh(first) }
        runCurrent()
        val worker = async { coordinator.refresh(first) }
        val other = async { coordinator.refresh(sibling) }
        runCurrent()
        assertEquals(listOf("first", "sibling"), seen)
        assertTrue(other.isCompleted)
        gate.complete(Unit)
        foreground.await(); worker.await()
        assertEquals(listOf("first", "sibling", "rotated"), seen)
        assertEquals("rotated", prefs.loadCredential(first)?.accessToken)
        assertEquals("sibling", prefs.loadCredential(sibling)?.accessToken)
        assertEquals(setOf(first.id, sibling.id), coordinator.quotas.value.keys)
    }

    @Test fun `delete discards late quota without deleting sibling or changing widget pin`() = runTest {
        val first = save("first")
        val sibling = save("sibling")
        cache.saveSelectedConnections(1, setOf(first.id))
        coordinator.refresh(sibling)
        val gate = CompletableDeferred<Unit>()
        fetch = { gate.await(); success(it.connection) }
        val late = async { coordinator.refresh(first) }
        runCurrent()
        assertTrue(coordinator.delete(first))
        gate.complete(Unit)
        assertNull(late.await())
        assertNull(prefs.loadCredential(first))
        assertNull(cache.getCachedQuota(first))
        assertEquals(setOf(sibling.id), coordinator.quotas.value.keys)
        assertNotNull(cache.getCachedQuota(sibling))
        assertEquals(setOf(first.id), cache.getSelectedConnections(1))
    }

    @Test fun `late token rotation after delete is rejected without cancelling sibling refresh`() = runTest {
        val first = save("first")
        val sibling = save("sibling")
        val gate = CompletableDeferred<Unit>()
        fetch = {
            if (it.connection.id == first.id) { gate.await(); it.replace(key("late")) }
            success(it.connection)
        }
        val late = async { coordinator.refresh(first) }
        runCurrent()
        coordinator.delete(first)
        gate.complete(Unit)
        assertNull(late.await())
        assertTrue(coordinator.refresh(sibling) is Result.Success)
        assertNull(prefs.loadCredential(first))
    }

    @Test fun `cancelled validation cannot publish account and releases refresh lock`() = runTest {
        val draft = AccountConnection.create(AiService.OPENCODE_GO)
        val gate = CompletableDeferred<Unit>()
        fetch = { gate.await(); success(it.connection) }
        val pending = async { coordinator.validateAndSave(draft, key("draft")) }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(prefs.loadConnections().isEmpty())
        assertTrue(coordinator.quotas.value.isEmpty())
        gate.complete(Unit)
        assertTrue(coordinator.validateAndSave(draft, key("valid")) is Result.Success)
        assertEquals("valid", prefs.loadCredential(draft)?.accessToken)
    }

    @Test fun `reconnect invalidates queued old-generation refresh and replaces only its cache`() = runTest {
        val first = save("first")
        val sibling = save("sibling")
        coordinator.refresh(first); coordinator.refresh(sibling)
        val replacement = first.reconnect()
        assertTrue(coordinator.validateAndSave(replacement, key("new"), first) is Result.Success)
        assertNull(coordinator.refresh(first))
        assertNull(cache.getCachedQuota(first))
        assertNotNull(cache.getCachedQuota(replacement))
        assertNotNull(cache.getCachedQuota(sibling))
        assertEquals("new", prefs.loadCredential(replacement)?.accessToken)
    }

    private fun save(token: String): AccountConnection = AccountConnection.create(AiService.OPENCODE_GO, token).also {
        check(prefs.saveValidatedConnection(it, key(token)))
    }
    private fun key(token: String) = Credential.OpenCodeGoCredential(token)
    private fun success(connection: AccountConnection) = Result.Success(QuotaInfo(connection.service,
        listOf(UsageWindow("5-Hour", 0.25, null)), null, fetchedAt = Instant.ofEpochSecond(1000)))
}
