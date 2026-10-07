package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenCodeGoCliParser {
    fun parse(capture: CliProcess.Result): Result<QuotaInfo, AppError> {
        if (capture.timedOut) return Result.Failure(AppError.NetworkError("OpenCode Go request timed out."))
        if (capture.stdoutTruncated || capture.stderrTruncated) {
            return Result.Failure(AppError.ParseError("Native CLI output exceeded its size limit."))
        }
        return try {
            val envelopes = Json.parseToJsonElement(capture.stdout).jsonArray
            require(envelopes.size == 1)
            val envelope = envelopes.single().jsonObject
            require(envelope["provider"]?.jsonPrimitive?.content == "opencodego")
            require(envelope["source"]?.jsonPrimitive?.content == "api")
            val error = envelope["error"]?.takeUnless { it == JsonNull }?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val mapped = when {
                    message.contains("credentials are invalid or expired", ignoreCase = true) ->
                        AppError.AuthError(AiService.OPENCODE_GO, isTerminal = true)
                    message.contains("HTTP 429") -> AppError.RateLimited
                    message.contains("HTTP 503") -> AppError.ServiceUnavailable
                    message.contains("No OpenCode Go subscription") ->
                        AppError.NetworkError("No active OpenCode Go subscription is available for this key.")
                    else -> AppError.NetworkError("OpenCode Go query failed. Check your API key and connection.")
                }
                return Result.Failure(mapped)
            }
            require(capture.exitCode == 0)
            val usage = envelope.getValue("usage").jsonObject
            val windows = listOf("primary" to "5-Hour", "secondary" to "Weekly", "tertiary" to "Monthly")
                .mapNotNull { (name, label) ->
                    val window = usage[name]?.takeUnless { it == JsonNull }?.jsonObject ?: return@mapNotNull null
                    if (window["isSyntheticPlaceholder"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                    val percent = window["usedPercent"]?.jsonPrimitive?.doubleOrNull
                    require(percent != null && percent.isFinite() && percent >= 0)
                    UsageWindow(
                        label = label,
                        utilization = percent / 100.0,
                        resetsAt = window["resetsAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse)
                    )
                }
            require(windows.isNotEmpty())
            Result.Success(QuotaInfo(
                service = AiService.OPENCODE_GO,
                windows = windows,
                extraUsage = null,
                fetchedAt = Instant.parse(usage.getValue("updatedAt").jsonPrimitive.content)
            ))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected OpenCode Go response from the native CLI."))
        }
    }
}
