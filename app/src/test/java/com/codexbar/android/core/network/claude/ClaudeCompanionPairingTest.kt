package com.codexbar.android.core.network.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClaudeCompanionPairingTest {
    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val id = "5b017391-6dc4-4ab7-b0ad-2255dada62d7"

    @Test
    fun `parses a non browsable private network pairing code`() {
        val credential = ClaudeCompanionPairing.parse(
            "CBCLAUDE1|192.168.1.24|43823|$id|$key"
        )

        assertEquals("192.168.1.24", credential.host)
        assertEquals(43823, credential.port)
        assertEquals(id, credential.companionId)
        assertEquals(key, credential.sharedKeyBase64Url)
    }

    @Test
    fun `rejects DNS public URL and extra pairing fields`() {
        listOf(
            "CBCLAUDE1|example.com|43823|$id|$key",
            "CBCLAUDE1|8.8.8.8|43823|$id|$key",
            "codexbar://claude-pair?v=1&address=127.0.0.1&port=43823&id=$id&key=$key",
            "CBCLAUDE1|127.0.0.1|43823|$id|$key|extra"
        ).forEach { pairing ->
            assertThrows(IllegalArgumentException::class.java) {
                ClaudeCompanionPairing.parse(pairing)
            }
        }
    }
}
