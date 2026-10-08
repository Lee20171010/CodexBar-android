package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.ReportedMoney
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowKind
import java.time.Instant
import kotlinx.serialization.json.*

object OpenRouterCliParser {
    fun parse(capture: CliProcess.Result): Result<QuotaInfo, AppError> {
        if (capture.timedOut) return Result.Failure(AppError.NetworkError("OpenRouter request timed out."))
        if (capture.stdoutTruncated || capture.stderrTruncated) {
            return Result.Failure(AppError.ParseError("Native CLI output exceeded its size limit."))
        }
        return try {
            val envelopes = Json.parseToJsonElement(capture.stdout).jsonArray
            require(envelopes.size == 1)
            val envelope = envelopes.single().jsonObject
            require(envelope["provider"]?.jsonPrimitive?.content == "openrouter")
            require(envelope["source"]?.jsonPrimitive?.content == "api")
            val error = envelope["error"]?.takeUnless { it == JsonNull }?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                return Result.Failure(when {
                    message.contains("HTTP 401") || message.contains("HTTP 403") || message.contains("credentials are invalid", true) ->
                        AppError.AuthError(AiService.OPENROUTER, isTerminal = true)
                    message.contains("HTTP 429") -> AppError.RateLimited
                    message.contains("HTTP 503") -> AppError.ServiceUnavailable
                    else -> AppError.NetworkError("OpenRouter query failed. Check your key and connection.")
                })
            }
            require(capture.exitCode == 0)
            val usage = envelope.getValue("usage").jsonObject
            val fetchedAt = Instant.parse(usage.getValue("updatedAt").jsonPrimitive.content)
            val primary = usage["primary"]?.takeUnless { it == JsonNull }?.jsonObject
            val windows = if (primary == null || primary["isSyntheticPlaceholder"]?.jsonPrimitive?.booleanOrNull == true) {
                emptyList()
            } else {
                val percent = primary.getValue("usedPercent").jsonPrimitive.double
                require(percent.isFinite() && percent >= 0)
                // Spending cap is not a timed rate limit. Do not infer a reset cadence.
                listOf(UsageWindow("API key budget", percent / 100.0, null,
                    id = "api-key-budget", kind = UsageWindowKind.BUDGET))
            }
            val cost = usage["providerCost"]?.takeUnless { it == JsonNull }?.jsonObject
            fun amount(name: String): Double? = cost?.get(name)?.takeUnless { it == JsonNull }?.let {
                it.jsonPrimitive.double.also { number -> require(number.isFinite() && number >= 0) }
            }
            val balance = amount("balance")
            val spent = amount("used")
            require(windows.isNotEmpty() || balance != null || spent != null)
            val currency = cost?.get("currencyCode")?.jsonPrimitive?.content ?: "USD"
            require(currency == "USD") // OpenRouter's public counters are denominated in USD.
            val period = cost?.get("period")?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
            require(period == null || (period.length <= 120 && period.none(Char::isISOControl)))
            val moneyAt = cost?.get("updatedAt")?.jsonPrimitive?.content?.let(Instant::parse) ?: fetchedAt
            Result.Success(QuotaInfo(AiService.OPENROUTER, windows, null, fetchedAt = fetchedAt,
                money = ReportedMoney(balance, spent, currency, period, moneyAt), source = "native-api"))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected OpenRouter response from the native CLI."))
        }
    }
}
