package com.codexbar.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Credential key-page URLs must only point at pages verified to issue the exact credential
 * this app accepts. OAuth-token providers and providers without a verified key page must
 * stay null so the UI never sends users to a page that cannot produce the right credential.
 */
class AiServiceKeyPageUrlTest {

    @Test
    fun `oauth and unverified providers have no key page`() {
        // Claude/Codex/Gemini use OAuth tokens from their own CLIs, not web API keys.
        assertNull(AiService.CLAUDE.keyPageUrl)
        assertNull(AiService.CODEX.keyPageUrl)
        assertNull(AiService.GEMINI.keyPageUrl)
        // Copilot needs a GitHub OAuth token; github.com/settings/tokens would create a PAT instead.
        assertNull(AiService.COPILOT.keyPageUrl)
    }

    @Test
    fun `linked key pages match the verified official URLs`() {
        assertEquals("https://opencode.ai/auth", AiService.OPENCODE_GO.keyPageUrl)
        assertEquals("https://platform.deepseek.com/api_keys", AiService.DEEPSEEK.keyPageUrl)
        assertEquals("https://openrouter.ai/keys", AiService.OPENROUTER.keyPageUrl)
        assertEquals("https://venice.ai/settings/api", AiService.VENICE.keyPageUrl)
        assertEquals("https://z.ai/manage-apikey", AiService.ZAI.keyPageUrl)
        assertEquals("https://app.factory.ai/settings/api-keys", AiService.FACTORY.keyPageUrl)
        assertEquals("https://ampcode.com/settings", AiService.AMP.keyPageUrl)
        // Coding Plan keys come from the Coding Plan console page, not the pay-as-you-go key page.
        assertEquals(
            "https://modelstudio.console.alibabacloud.com/ap-southeast-1/?tab=coding-plan#/efm/coding_plan",
            AiService.ALIBABA_CODING_PLAN.keyPageUrl
        )
        assertEquals("https://hyper.charm.land", AiService.HYPER.keyPageUrl)
        // Kimi console is a navigation link only; the button label stays neutral ("Open console").
        assertEquals("https://www.kimi.com/code/console", AiService.KIMI.keyPageUrl)
        // MiniMax console root likewise — exact key-issuing page unverified.
        assertEquals("https://platform.minimax.io", AiService.MINIMAX.keyPageUrl)
    }

    @Test
    fun `every linked url is plain https without embedded credentials`() {
        for (service in AiService.entries) {
            val url = service.keyPageUrl ?: continue
            val parsed = URI(url)
            assertEquals("https", parsed.scheme)
            assertTrue("must have a host: $url", !parsed.host.isNullOrBlank())
            assertNull("must not embed credentials: $url", parsed.userInfo)
            assertTrue("no whitespace: $url", url.none(Char::isWhitespace))
            val query = parsed.rawQuery.orEmpty()
            assertTrue(
                "query must carry no token-like parameter: $url",
                query.split('&').none { it.contains("key", ignoreCase = true) || it.contains("token", ignoreCase = true) }
            )
        }
    }

    @Test
    fun `api-key providers are linked except the documented exclusions`() {
        val exclusions = setOf(AiService.COPILOT)
        for (service in AiService.entries.filter { it.usesApiKey }) {
            if (service in exclusions) assertNull(service.keyPageUrl)
            else assertTrue("missing key page for ${service.name}", service.keyPageUrl != null)
        }
    }
}
