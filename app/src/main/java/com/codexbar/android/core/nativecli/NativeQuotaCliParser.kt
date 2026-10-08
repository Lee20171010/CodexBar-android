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
import kotlinx.serialization.json.longOrNull

object NativeQuotaCliParser {
    fun parse(capture: CliProcess.Result, service: AiService = AiService.OPENCODE_GO): Result<QuotaInfo, AppError> {
        val provider = when (service) {
            AiService.OPENCODE_GO -> "opencodego"
            AiService.COPILOT -> "copilot"
            AiService.CODEX -> "codex"
            else -> error("Unsupported quota provider")
        }
        if (capture.timedOut) return Result.Failure(AppError.NetworkError("${service.displayName} request timed out."))
        if (capture.stdoutTruncated || capture.stderrTruncated) {
            return Result.Failure(AppError.ParseError("Native CLI output exceeded its size limit."))
        }
        return try {
            val envelopes = Json.parseToJsonElement(capture.stdout).jsonArray
            require(envelopes.size == 1)
            val envelope = envelopes.single().jsonObject
            require(envelope["provider"]?.jsonPrimitive?.content == provider)
            require(envelope["source"]?.jsonPrimitive?.content == if (service == AiService.CODEX) "oauth" else "api")
            val error = envelope["error"]?.takeUnless { it == JsonNull }?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val mapped = when {
                    message.contains("credentials are invalid or expired", ignoreCase = true) ||
                        (service == AiService.CODEX && message.contains("Codex OAuth token expired or invalid")) ||
                        (service == AiService.COPILOT && message.contains("NSURLErrorDomain") && Regex("-1013(?![0-9])").containsMatchIn(message)) ->
                        AppError.AuthError(service, isTerminal = true)
                    message.contains("HTTP 429") || message.contains("Codex API error 429") -> AppError.RateLimited
                    Regex("(?:HTTP |Codex API error )5[0-9]{2}").containsMatchIn(message) -> AppError.ServiceUnavailable
                    message.contains("No OpenCode Go subscription") ->
                        AppError.NetworkError("No active OpenCode Go subscription is available for this key.")
                    else -> AppError.NetworkError("${service.displayName} query failed. Check your credential and connection.")
                }
                return Result.Failure(mapped)
            }
            require(capture.exitCode == 0)
            val usage = envelope.getValue("usage").jsonObject
            val labels = when (service) {
                AiService.COPILOT -> listOf("primary" to "Premium", "secondary" to "Chat")
                AiService.CODEX -> listOf("primary" to "Primary", "secondary" to "Secondary")
                else -> listOf("primary" to "5-Hour", "secondary" to "Weekly", "tertiary" to "Monthly")
            }
            val windows = labels
                .mapNotNull { (name, label) ->
                    val window = usage[name]?.takeUnless { it == JsonNull }?.jsonObject ?: return@mapNotNull null
                    if (window["isSyntheticPlaceholder"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                    val percent = window["usedPercent"]?.jsonPrimitive?.doubleOrNull
                    require(percent != null && percent.isFinite() && percent >= 0)
                    val minutes = window["windowMinutes"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..525600 }
                    UsageWindow(
                        label = if (service == AiService.CODEX) when (minutes) {
                            300L -> "5-Hour"
                            10080L -> "7-Day"
                            null -> label
                            else -> "$minutes min"
                        } else label,
                        utilization = percent / 100.0,
                        resetsAt = window["resetsAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
                        id = name,
                        durationSeconds = minutes?.times(60)
                    )
                }.toMutableList()
            if (service == AiService.CODEX) {
                usage["extraRateWindows"]?.takeUnless { it == JsonNull }?.jsonArray?.forEach { value ->
                    // Optional model pools cannot invalidate a good principal quota response.
                    runCatching {
                        val named = value.jsonObject
                        if (named["usageKnown"]?.jsonPrimitive?.booleanOrNull == false) return@runCatching
                        val window = named.getValue("window").jsonObject
                        if (window["isSyntheticPlaceholder"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching
                        val percent = window.getValue("usedPercent").jsonPrimitive.doubleOrNull!!
                        val id = named.getValue("id").jsonPrimitive.content
                        val title = named.getValue("title").jsonPrimitive.content
                        require(percent.isFinite() && percent >= 0 && id.length in 1..256 && title.length in 1..256)
                        require((id + title).none(Char::isISOControl))
                        windows.add(UsageWindow(title, percent / 100,
                            window["resetsAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
                            id = "extra:$id", supplemental = true,
                            durationSeconds = window["windowMinutes"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..525600 }?.times(60)))
                    }
                }
            }
            val plan = if (service == AiService.COPILOT || service == AiService.CODEX) usage["loginMethod"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) } else null
            require(windows.isNotEmpty() || (service == AiService.COPILOT && plan != null))
            Result.Success(QuotaInfo(
                service = service,
                windows = windows,
                tier = plan,
                extraUsage = null,
                fetchedAt = Instant.parse(usage.getValue("updatedAt").jsonPrimitive.content),
                source = if (service == AiService.CODEX) "native-oauth" else "native-api"
            ))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected ${service.displayName} response from the native CLI."))
        }
    }
}
