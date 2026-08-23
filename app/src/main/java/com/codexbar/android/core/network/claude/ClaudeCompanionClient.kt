package com.codexbar.android.core.network.claude

import com.codexbar.android.core.domain.model.Credential
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
open class ClaudeCompanionClient @Inject constructor(
    private val json: Json
) {
    private val secureRandom = SecureRandom()

    open suspend fun fetchSnapshot(
        credential: Credential.ClaudeCompanionCredential,
        now: Instant = Instant.now()
    ): ClaudeCompanionSnapshot = withContext(Dispatchers.IO) {
        ClaudeCompanionPairing.validate(credential)
        val masterKey = decodeUrlBase64(credential.sharedKeyBase64Url)
        val authKey = deriveKey(masterKey, AUTH_KEY_CONTEXT)
        val encryptionKey = deriveKey(masterKey, ENCRYPTION_KEY_CONTEXT)
        val nonce = ByteArray(REQUEST_NONCE_BYTES).also(secureRandom::nextBytes).toUrlBase64()
        val requestedAt = now.epochSecond
        val signature = hmacSha256(
            authKey,
            requestCanonical(
                companionId = credential.companionId,
                requestedAtEpochSeconds = requestedAt,
                nonce = nonce
            ).toByteArray(StandardCharsets.UTF_8)
        ).toUrlBase64()
        val request = ClaudeCompanionRequest(
            protocolVersion = PROTOCOL_VERSION,
            companionId = credential.companionId,
            requestedAtEpochSeconds = requestedAt,
            nonce = nonce,
            signature = signature
        )

        val responseLine = Socket().use { socket ->
            socket.soTimeout = IO_TIMEOUT_MILLIS
            socket.connect(
                InetSocketAddress(credential.host, credential.port),
                CONNECT_TIMEOUT_MILLIS
            )
            socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(request))
                writer.newLine()
                writer.flush()
                readLimitedLine(socket)
            }
        }

        val envelope = runCatching {
            json.decodeFromString<ClaudeCompanionEnvelope>(responseLine)
        }.getOrElse { throw ClaudeCompanionProtocolException("Invalid companion envelope", it) }
        validateEnvelope(envelope, credential, nonce, now)
        val plaintext = decryptEnvelope(envelope, encryptionKey)
        val snapshot = runCatching {
            json.decodeFromString<ClaudeCompanionSnapshot>(plaintext)
        }.getOrElse { throw ClaudeCompanionProtocolException("Invalid quota snapshot", it) }
        validateSnapshot(snapshot, now)
        snapshot
    }

    private fun readLimitedLine(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) throw IOException("Companion closed the connection")
            if (next == '\n'.code) {
                return output.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
            }
            output.write(next)
        }
        throw ClaudeCompanionProtocolException("Companion response is too large")
    }

    private fun validateEnvelope(
        envelope: ClaudeCompanionEnvelope,
        credential: Credential.ClaudeCompanionCredential,
        requestNonce: String,
        now: Instant
    ) {
        if (
            envelope.protocolVersion != PROTOCOL_VERSION ||
            envelope.companionId != credential.companionId ||
            envelope.requestNonce != requestNonce ||
            kotlin.math.abs(envelope.sentAtEpochSeconds - now.epochSecond) > MAX_CLOCK_SKEW_SECONDS
        ) {
            throw ClaudeCompanionAuthenticationException(
                "Companion response authentication failed"
            )
        }
    }

    private fun decryptEnvelope(
        envelope: ClaudeCompanionEnvelope,
        encryptionKey: ByteArray
    ): String {
        return try {
            val iv = decodeUrlBase64(envelope.iv)
            if (iv.size != GCM_IV_BYTES) {
                throw ClaudeCompanionAuthenticationException("Invalid companion IV")
            }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(encryptionKey, AES_ALGORITHM),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(responseCanonical(envelope).toByteArray(StandardCharsets.UTF_8))
            String(
                cipher.doFinal(decodeUrlBase64(envelope.ciphertext)),
                StandardCharsets.UTF_8
            )
        } catch (error: ClaudeCompanionAuthenticationException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw ClaudeCompanionAuthenticationException(
                "Companion response authentication failed",
                error
            )
        } catch (error: IllegalArgumentException) {
            throw ClaudeCompanionAuthenticationException(
                "Companion response encoding is invalid",
                error
            )
        }
    }

    private fun validateSnapshot(snapshot: ClaudeCompanionSnapshot, now: Instant) {
        if (
            snapshot.schemaVersion != SNAPSHOT_SCHEMA_VERSION ||
            snapshot.source != SNAPSHOT_SOURCE ||
            snapshot.cliVersion.isBlank() ||
            snapshot.cliVersion.length > MAX_TEXT_LENGTH ||
            snapshot.windows.isEmpty() ||
            snapshot.windows.size > MAX_WINDOWS ||
            snapshot.generatedAtEpochSeconds > now.epochSecond + MAX_CLOCK_SKEW_SECONDS ||
            now.epochSecond - snapshot.generatedAtEpochSeconds > MAX_SNAPSHOT_AGE_SECONDS
        ) {
            throw ClaudeCompanionProtocolException("Companion snapshot failed validation")
        }
        snapshot.tier?.let {
            if (it.isBlank() || it.length > MAX_TEXT_LENGTH || it.hasControlCharacter()) {
                throw ClaudeCompanionProtocolException("Invalid companion tier")
            }
        }
        val labels = mutableSetOf<String>()
        snapshot.windows.forEach { window ->
            if (
                window.label.isBlank() ||
                window.label.length > MAX_LABEL_LENGTH ||
                window.label.hasControlCharacter() ||
                !window.usedFraction.isFinite() ||
                window.usedFraction !in 0.0..1.0 ||
                !labels.add(window.label.lowercase()) ||
                window.resetsAtEpochSeconds?.let {
                    it < snapshot.generatedAtEpochSeconds - MAX_CLOCK_SKEW_SECONDS ||
                        it > snapshot.generatedAtEpochSeconds + MAX_RESET_HORIZON_SECONDS
                } == true
            ) {
                throw ClaudeCompanionProtocolException("Invalid companion quota window")
            }
        }
    }

    private fun String.hasControlCharacter(): Boolean = any(Char::isISOControl)

    companion object {
        const val PROTOCOL_VERSION = 1
        const val SNAPSHOT_SCHEMA_VERSION = 1
        const val SNAPSHOT_SOURCE = "claude-cli-terminal"
        internal const val AUTH_KEY_CONTEXT = "codexbar-claude-auth-v1"
        internal const val ENCRYPTION_KEY_CONTEXT = "codexbar-claude-encryption-v1"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val IO_TIMEOUT_MILLIS = 8_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val REQUEST_NONCE_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_CLOCK_SKEW_SECONDS = 120L
        private const val MAX_SNAPSHOT_AGE_SECONDS = 2 * 60 * 60L
        private const val MAX_RESET_HORIZON_SECONDS = 31 * 24 * 60 * 60L
        private const val MAX_WINDOWS = 8
        private const val MAX_TEXT_LENGTH = 64
        private const val MAX_LABEL_LENGTH = 32
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_ALGORITHM = "AES"

        internal fun requestCanonical(
            companionId: String,
            requestedAtEpochSeconds: Long,
            nonce: String
        ): String = listOf(
            PROTOCOL_VERSION.toString(),
            companionId,
            requestedAtEpochSeconds.toString(),
            nonce
        ).joinToString("\n")

        internal fun responseCanonical(envelope: ClaudeCompanionEnvelope): String = listOf(
            envelope.protocolVersion.toString(),
            envelope.companionId,
            envelope.requestNonce,
            envelope.sentAtEpochSeconds.toString()
        ).joinToString("\n")

        internal fun deriveKey(masterKey: ByteArray, context: String): ByteArray {
            return hmacSha256(masterKey, context.toByteArray(StandardCharsets.UTF_8))
        }

        internal fun hmacSha256(key: ByteArray, payload: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(payload)
        }

        internal fun ByteArray.toUrlBase64(): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(this)

        internal fun decodeUrlBase64(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    }
}

class ClaudeCompanionAuthenticationException(
    message: String,
    cause: Throwable? = null
) : GeneralSecurityException(message, cause)

class ClaudeCompanionProtocolException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
