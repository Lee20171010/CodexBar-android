package com.codexbar.android.core.auth

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexTokenClaimsTest {

    @Test
    fun `extracts account id from OpenAI auth namespace`() {
        val token = jwt(
            """{"https://api.openai.com/auth":{"chatgpt_account_id":"acct_team_123"}}"""
        )

        assertEquals("acct_team_123", codexAccountId(idToken = token, accessToken = "unused"))
    }

    @Test
    fun `extracts access token expiry`() {
        val token = jwt("""{"exp":2000000000}""")

        assertEquals(Instant.ofEpochSecond(2_000_000_000L), codexTokenExpiresAt(token))
    }

    @Test
    fun `rejects malformed token claims`() {
        assertNull(codexAccountId(idToken = "not-a-jwt", accessToken = "also-invalid"))
        assertNull(codexTokenExpiresAt("not-a-jwt"))
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{}".toByteArray(StandardCharsets.UTF_8))
        val claims = encoder.encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$header.$claims.signature"
    }
}
