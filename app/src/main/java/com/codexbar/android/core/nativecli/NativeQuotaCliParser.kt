package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.ReportedMoney
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageDataConfidence
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.model.UsageWindowSlot
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
            AiService.VENICE -> "venice"
            AiService.AMP -> "amp"
            AiService.ZAI -> "zai"
            AiService.FACTORY -> "factory"
            // Envelope carries ProviderID rawValue, not the "alibaba-coding-plan" cliName.
            AiService.ALIBABA_CODING_PLAN -> "alibaba"
            AiService.HYPER -> "hyper"
            AiService.KIMI -> "kimi"
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
                        (service == AiService.COPILOT && message.contains("NSURLErrorDomain") && Regex("-1013(?![0-9])").containsMatchIn(message)) ||
                        (service == AiService.VENICE && message.contains("Venice API error: HTTP 401")) ||
                        (service == AiService.AMP && message.contains("Amp access token is invalid or expired")) ||
                        (service == AiService.ZAI && message.contains("z.ai quota API error: HTTP 401")) ||
                        (service == AiService.FACTORY && message.contains("Droid API authentication failed")) ||
                        (service == AiService.HYPER && (message.contains("Charm Hyper API key rejected") ||
                            message.contains("cannot access credits"))) ||
                        (service == AiService.KIMI && message.contains("Kimi Code API key is invalid or expired")) ->
                        AppError.AuthError(service, isTerminal = true)
                    message.contains("HTTP 429") || message.contains("Codex API error 429") ||
                        (service == AiService.HYPER && message.contains("rate limited")) -> AppError.RateLimited
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
                AiService.VENICE -> listOf("primary" to "Balance")
                AiService.AMP -> listOf("primary" to "Amp", "secondary" to "Orb")
                AiService.ZAI -> listOf("primary" to "5-Hour", "secondary" to "Weekly")
                // CLI maps weekly pool→primary, session pool→secondary; labels follow actual minutes below.
                AiService.KIMI -> listOf("primary" to "Weekly", "secondary" to "5-Hour")
                AiService.FACTORY -> listOf("primary" to "Standard", "secondary" to "Premium", "tertiary" to "Monthly")
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
                        label = if (service == AiService.CODEX || service == AiService.FACTORY || service == AiService.KIMI) when (minutes) {
                            300L -> "5-Hour"
                            10080L -> "7-Day"
                            null -> label
                            else -> "$minutes min"
                        } else label,
                        utilization = percent / 100.0,
                        resetsAt = window["resetsAt"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
                        id = name,
                        durationSeconds = minutes?.times(60),
                        slot = UsageWindowSlot.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    )
                }.toMutableList()
            if (service == AiService.CODEX || service == AiService.AMP || service == AiService.ZAI || service == AiService.FACTORY || service == AiService.KIMI) {
                usage["extraRateWindows"]?.takeUnless { it == JsonNull }?.jsonArray?.forEach { value ->
                    // Optional named windows cannot invalidate a good principal quota response.
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
            val plan = if (service == AiService.COPILOT || service == AiService.CODEX || service == AiService.AMP || service == AiService.ZAI || service == AiService.FACTORY || service == AiService.ALIBABA_CODING_PLAN || service == AiService.HYPER || service == AiService.KIMI) usage["loginMethod"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) } else null
            val fetchedAt = Instant.parse(usage.getValue("updatedAt").jsonPrimitive.content)
            // Factory's providerCost.used carries the remaining extra-usage balance in USD
            // (upstream ProviderCostSnapshot(used: cents/100, limit: 0, period: "Extra usage balance")).
            val money = when (service) {
                AiService.FACTORY -> usage["providerCost"]?.takeUnless { it == JsonNull }?.jsonObject?.let { cost ->
                    val used = cost["used"]?.jsonPrimitive?.doubleOrNull
                    require(used != null && used.isFinite() && used >= 0)
                    val period = cost["period"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.contentOrNull
                    require(period == null || (period.length <= 120 && period.none(Char::isISOControl)))
                    ReportedMoney(
                        balance = used,
                        spent = null,
                        currency = cost["currencyCode"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.contentOrNull ?: "USD",
                        period = period,
                        fetchedAt = cost["updatedAt"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.contentOrNull?.let(Instant::parse) ?: fetchedAt
                    )
                }
                // Charm Hyper is balance-only: hyper.js emits the raw number as usageValue on the
                // details "Hypercredits"/"Balance" row (never parse the formatted display string).
                // "HC" is the provider credit unit, not fiat — rendered verbatim, no conversion.
                AiService.HYPER -> usage["details"]?.takeUnless { it == JsonNull }?.jsonArray?.firstNotNullOfOrNull { element ->
                    val section = element.jsonObject
                    if (section["title"]?.jsonPrimitive?.contentOrNull != "Hypercredits") return@firstNotNullOfOrNull null
                    section["rows"]?.takeUnless { it == JsonNull }?.jsonArray
                        ?.firstOrNull { it.jsonObject["label"]?.jsonPrimitive?.contentOrNull == "Balance" }
                        ?.jsonObject?.get("usageValue")?.takeUnless { it == JsonNull }?.jsonPrimitive?.doubleOrNull
                }?.let { balance ->
                    require(balance.isFinite() && balance >= 0)
                    ReportedMoney(balance = balance, spent = null, currency = "HC", period = null, fetchedAt = fetchedAt)
                }
                else -> null
            }
            require(windows.isNotEmpty() || (service == AiService.COPILOT && plan != null) ||
                (service == AiService.HYPER && money != null))
            Result.Success(QuotaInfo(
                service = service,
                windows = windows,
                tier = plan,
                extraUsage = null,
                fetchedAt = fetchedAt,
                money = money,
                source = if (service == AiService.CODEX) "native-oauth" else "native-api",
                credits = if (service == AiService.CODEX) CodexReportedCredits.balance(envelope["credits"]) else null,
                resetInventory = if (service == AiService.CODEX) CodexReportedCredits.inventory(usage["codexResetCredits"]) else null,
                dataConfidence = when (usage["dataConfidence"]?.jsonPrimitive?.contentOrNull) {
                    "exact" -> UsageDataConfidence.EXACT
                    "estimated" -> UsageDataConfidence.ESTIMATED
                    "percentOnly" -> UsageDataConfidence.PERCENT_ONLY
                    else -> UsageDataConfidence.UNKNOWN
                }
            ))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected ${service.displayName} response from the native CLI."))
        }
    }
}
