package com.codexbar.android.core.network.claude

import com.codexbar.android.core.domain.model.Credential
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ClaudeCompanionClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val masterKey = ByteArray(32) { index -> index.toByte() }

    @Test
    fun `authenticates and decrypts a bounded Claude snapshot`() = runTest {
        val now = Instant.ofEpochSecond(1_750_000_000L)
        val fixture = startServer(now = now, tamperCiphertext = false)

        val snapshot = ClaudeCompanionClient(json).fetchSnapshot(fixture.credential, now)
        fixture.thread.join(5_000)

        assertNull(fixture.failure.get())
        assertEquals("official-cli", snapshot.cliVersion)
        assertEquals("5-Hour", snapshot.windows.single().label)
        assertEquals(0.25, snapshot.windows.single().usedFraction, 0.001)
    }

    @Test
    fun `rejects a tampered encrypted Claude response`() {
        val now = Instant.ofEpochSecond(1_750_000_000L)
        val fixture = startServer(now = now, tamperCiphertext = true)

        assertThrows(ClaudeCompanionAuthenticationException::class.java) {
            kotlinx.coroutines.runBlocking {
                ClaudeCompanionClient(json).fetchSnapshot(fixture.credential, now)
            }
        }
        fixture.thread.join(5_000)
        assertNull(fixture.failure.get())
    }

    private fun startServer(now: Instant, tamperCiphertext: Boolean): ServerFixture {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val companionId = UUID.randomUUID().toString()
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                server.use { listener ->
                    listener.accept().use { socket ->
                        val requestLine = socket.getInputStream().bufferedReader().readLine()
                        val request = json.decodeFromString<ClaudeCompanionRequest>(requestLine)
                        val authKey = ClaudeCompanionClient.deriveKey(
                            masterKey,
                            ClaudeCompanionClient.AUTH_KEY_CONTEXT
                        )
                        val expectedSignature = ClaudeCompanionClient.hmacSha256(
                            authKey,
                            ClaudeCompanionClient.requestCanonical(
                                request.companionId,
                                request.requestedAtEpochSeconds,
                                request.nonce
                            ).toByteArray(StandardCharsets.UTF_8)
                        )
                        check(
                            MessageDigest.isEqual(
                                expectedSignature,
                                ClaudeCompanionClient.decodeUrlBase64(request.signature)
                            )
                        )

                        val iv = ByteArray(12) { index -> (index + 1).toByte() }
                        val envelopeTemplate = ClaudeCompanionEnvelope(
                            protocolVersion = 1,
                            companionId = companionId,
                            requestNonce = request.nonce,
                            sentAtEpochSeconds = now.epochSecond,
                            iv = with(ClaudeCompanionClient) { iv.toUrlBase64() },
                            ciphertext = ""
                        )
                        val snapshot = ClaudeCompanionSnapshot(
                            schemaVersion = 1,
                            source = "claude-cli-terminal",
                            generatedAtEpochSeconds = now.epochSecond,
                            cliVersion = "official-cli",
                            tier = "Max 5x",
                            windows = listOf(
                                ClaudeCompanionWindow(
                                    "5-Hour",
                                    0.25,
                                    now.epochSecond + 5_400
                                )
                            )
                        )
                        val encryptionKey = ClaudeCompanionClient.deriveKey(
                            masterKey,
                            ClaudeCompanionClient.ENCRYPTION_KEY_CONTEXT
                        )
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(
                            Cipher.ENCRYPT_MODE,
                            SecretKeySpec(encryptionKey, "AES"),
                            GCMParameterSpec(128, iv)
                        )
                        cipher.updateAAD(
                            ClaudeCompanionClient.responseCanonical(envelopeTemplate)
                                .toByteArray(StandardCharsets.UTF_8)
                        )
                        val encrypted = cipher.doFinal(
                            json.encodeToString(snapshot).toByteArray(StandardCharsets.UTF_8)
                        )
                        if (tamperCiphertext) encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
                        val envelope = envelopeTemplate.copy(
                            ciphertext = with(ClaudeCompanionClient) {
                                encrypted.toUrlBase64()
                            }
                        )
                        socket.getOutputStream().bufferedWriter().use { writer ->
                            writer.write(json.encodeToString(envelope))
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply {
            isDaemon = true
            start()
        }
        val credential = Credential.ClaudeCompanionCredential(
            host = "127.0.0.1",
            port = server.localPort,
            companionId = companionId,
            sharedKeyBase64Url = with(ClaudeCompanionClient) { masterKey.toUrlBase64() }
        )
        return ServerFixture(credential, thread, failure)
    }

    private data class ServerFixture(
        val credential: Credential.ClaudeCompanionCredential,
        val thread: Thread,
        val failure: AtomicReference<Throwable?>
    )
}
