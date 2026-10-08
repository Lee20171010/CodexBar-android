package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CopilotCliParserTest {
    private fun parse(usage: String) = NativeQuotaCliParser.parse(CliProcess.Result(0, false, 1,
        """[{"provider":"copilot","source":"api","usage":{$usage,"updatedAt":"2026-10-08T00:00:00Z"}}]""", "", false, false), AiService.COPILOT)

    @Test fun `chat-only and over-quota windows preserve labels without inventing premium`() {
        val quota = (parse(""""secondary":{"usedPercent":125,"resetsAt":"2026-11-01T00:00:00Z"},"loginMethod":"Free"""") as Result.Success).value
        assertEquals(AiService.COPILOT, quota.service)
        assertEquals("Chat", quota.windows.single().label)
        assertEquals(1.25, quota.windows.single().utilization, 0.0)
        assertEquals("Free", quota.tier)
        val both = (parse(""""primary":{"usedPercent":0},"secondary":{"usedPercent":50}""") as Result.Success).value
        assertEquals(listOf("Premium", "Chat"), both.windows.map { it.label })
    }

    @Test fun `plan-only response stays unmetered and invalid measurements fail`() {
        assertTrue((parse(""""loginMethod":"Business"""") as Result.Success).value.windows.isEmpty())
        assertTrue(parse(""""primary":{"usedPercent":-1}""") is Result.Failure)
        assertTrue(parse(""""primary":{"usedPercent":null}""") is Result.Failure)
        assertTrue(parse(""""primary":null""") is Result.Failure)
    }

    @Test fun `native authentication rejection is sanitized and bound to Copilot`() {
        val text = """[{"provider":"copilot","source":"api","error":{"message":"NSURLErrorDomain error -1013. synthetic-secret"}}]"""
        val capture = CliProcess.Result(1, false, 1, text, "synthetic-secret", false, false)
        val error = (NativeQuotaCliParser.parse(capture, AiService.COPILOT) as Result.Failure).error
        assertTrue(error is AppError.AuthError)
        assertFalse(error.toString().contains("synthetic-secret"))
        assertTrue(NativeQuotaCliParser.parse(CliProcess.Result(1, false, 1,
            text.replace("api", "web"), "", false, false), AiService.COPILOT) is Result.Failure)
    }
}
