package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AccountBalance
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ParsedOpenCodeUsage(
    val windows: List<UsageWindow>,
    val renewsAt: Instant?
)

object OpenCodePayloadParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseSubscription(
        payload: String,
        now: Instant = Instant.now()
    ): ParsedOpenCodeUsage {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        val windows = linkedMapOf<String, WindowValues>()
        if (root != null) {
            root.collectWindows(windows)
        } else {
            WINDOW_NAMES.forEach { name ->
                payload.fallbackWindow(name, now)?.let { windows[name] = it }
            }
        }

        val rolling = windows["rolling"]?.toUsageWindow("5-Hour", now, FIVE_HOURS_SECONDS)
            ?: throw IllegalArgumentException("Missing rolling usage")
        val weekly = windows["weekly"]?.toUsageWindow("Weekly", now, WEEK_SECONDS)
        val monthly = windows["monthly"]?.toUsageWindow("Monthly", now, null)

        return ParsedOpenCodeUsage(
            windows = buildList {
                add(rolling)
                weekly?.let(::add)
                monthly?.let(::add)
            },
            renewsAt = root?.findRollingRenewal()?.second
        )
    }

    fun parseZenBalance(payload: String): AccountBalance? {
        val first = payload.firstOrNull { !it.isWhitespace() }
        val root = if (first == '{' || first == '[' || first == '"') {
            runCatching { json.parseToJsonElement(payload) }.getOrNull()
        } else null
        val content = payload.take(MAX_FALLBACK_LENGTH)
        val balance = if (root != null) {
            root.findBalance()
        } else {
            BALANCE_LABELLED_HTML.findAll(content)
                .firstOrNull { match ->
                    !content.isInsideDoubleQuotedString(match.range.first) &&
                        match.groups[1]?.range?.first?.let { !content.isInsideDoubleQuotedString(it) } == true
                }
                ?.groupValues?.get(1)?.toFiniteBalanceNumberOrNull()
                ?: EXPLICIT_BALANCE_FALLBACK.findAll(content)
                    .firstOrNull { !content.isInsideDoubleQuotedString(it.range.first) }
                    ?.groupValues?.get(1)?.toFiniteBalanceNumberOrNull()
        }
        return balance?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { AccountBalance(it, "USD") }
    }

    private fun JsonElement.collectWindows(
        output: MutableMap<String, WindowValues>,
        inheritedName: String? = null
    ) {
        when (this) {
            is JsonObject -> {
                toWindowValues()?.let { window ->
                    if (inheritedName != null) output.putIfAbsent(inheritedName, window)
                }
                entries.forEach { (key, value) ->
                    val name = WINDOW_NAMES.firstOrNull { normalize(key).contains(it) } ?: inheritedName
                    value.collectWindows(output, name)
                }
            }
            is JsonArray -> forEach { it.collectWindows(output, inheritedName) }
            else -> Unit
        }
    }

    private fun JsonObject.toWindowValues(): WindowValues? {
        val values = entries.associate { normalize(it.key) to it.value }

        val percent = values["usagepercent"]?.numberOrNull()
            ?: values["usedpercent"]?.numberOrNull()
            ?: values["percentused"]?.numberOrNull()
            ?: runCatching {
                val used = values["used"]?.numberOrNull()
                val limit = values["limit"]?.numberOrNull()
                if (used != null && limit != null && limit > 0) (used / limit).coerceIn(0.0, 1.0) else null
            }.getOrNull()
            ?: return null

        val resetInSec = values["resetinsec"]?.numberOrNull()?.toLong()
        if (resetInSec != null && resetInSec < 0) return null
        val resetInSeconds = resetInSec?.takeIf { it >= 0 }
            ?: values["resetinseconds"]?.numberOrNull()?.toLong()?.takeIf { it >= 0 }
        val resetAtInstant = values["resetat"]?.toInstantOrNull()
            ?: values["resetsat"]?.toInstantOrNull()

        return WindowValues(percent, resetInSeconds, resetAtInstant)
    }

    private fun JsonElement.findRollingRenewal(
        inherited: Instant? = null,
        inheritedName: String? = null
    ): Pair<Boolean, Instant?> = when (this) {
        is JsonObject -> {
            val renewal = entries.firstNotNullOfOrNull { (key, value) ->
                value.toInstantOrNull().takeIf { normalize(key) == "renewat" }
            } ?: inherited
            if (inheritedName == "rolling" && toWindowValues() != null) {
                true to renewal
            } else {
                entries.firstNotNullOfOrNull { (key, value) ->
                    val name = WINDOW_NAMES.firstOrNull { normalize(key).contains(it) } ?: inheritedName
                    value.findRollingRenewal(renewal, name).takeIf { it.first }
                } ?: (false to null)
            }
        }
        is JsonArray -> firstNotNullOfOrNull {
            it.findRollingRenewal(inherited, inheritedName).takeIf { result -> result.first }
        } ?: (false to null)
        else -> false to null
    }

    private fun JsonElement.findBalance(): Double? = when (this) {
        is JsonObject -> entries.firstNotNullOfOrNull { (key, value) ->
            if (isExplicitBalanceKey(key)) value.balanceNumberOrNull() else value.findBalance()
        }
        is JsonArray -> firstNotNullOfOrNull { it.findBalance() }
        else -> null
    }

    private fun isExplicitBalanceKey(key: String): Boolean =
        normalize(key) in EXPLICIT_BALANCE_KEYS

    fun payloadDiagnostic(payload: String): String {
        if (payload.isEmpty()) return "response=empty, chars=0, keys=[]"
        val trimmed = payload.trimStart()
        val root = if (trimmed.startsWith("<")) null else {
            runCatching { json.parseToJsonElement(payload) }.getOrNull()
        }
        val cls = if (trimmed.startsWith("<")) "html" else if (root != null) "json" else "text"
        val keys = if (root != null) {
            linkedSetOf<String>().also { root.collectDiagnosticKeys(it) }.sorted()
        } else {
            emptyList()
        }
        return (if (keys.isEmpty()) "response=$cls, chars=${payload.length}, keys=[]"
        else "response=$cls, chars=${payload.length}, keys=[${keys.joinToString()}]")
            .take(MAX_DIAGNOSTIC_LENGTH)
    }

    private fun JsonElement.collectDiagnosticKeys(output: MutableSet<String>) {
        if (output.size >= MAX_DIAGNOSTIC_KEYS) return
        when (this) {
            is JsonObject -> entries.forEach { (key, value) ->
                if (output.size < MAX_DIAGNOSTIC_KEYS && key in SAFE_DIAGNOSTIC_KEYS) output += key
                value.collectDiagnosticKeys(output)
            }
            is JsonArray -> forEach { it.collectDiagnosticKeys(output) }
            else -> Unit
        }
    }

    fun parseBillingZenBalance(payload: String): AccountBalance? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        val rawBalance = if (root != null) {
            findRawBillingBalance(root)
        } else {
            runCatching {
                BILLING_CUSTOMER_ID.findAll(payload)
                    .filter {
                        it.groupValues[1].isNotBlank() && !payload.isInsideDoubleQuotedString(it.range.first)
                    }
                    .firstNotNullOfOrNull { customer ->
                        val scope = payload.billingScopeAt(customer.range.first)
                        BILLING_RAW_BALANCE.findAll(scope)
                            .firstOrNull { scope.isAtObjectTopLevel(it.range.first) }
                            ?.groupValues?.get(1)?.toDoubleOrNull()
                    }
            }.getOrNull()
        }
        return rawBalance?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { AccountBalance(it / BILLING_SCALE, "USD") }
    }

    private fun String.isInsideDoubleQuotedString(index: Int): Boolean {
        var inside = false
        var escaped = false
        for (position in 0 until index) {
            when {
                escaped -> escaped = false
                inside && this[position] == '\\' -> escaped = true
                this[position] == '"' -> inside = !inside
            }
        }
        return inside
    }

    private fun String.billingScopeAt(index: Int): String {
        val objectStarts = ArrayDeque<Int>()
        var enclosingStart: Int? = null
        var inside = false
        var escaped = false
        for (position in indices) {
            if (position == index) enclosingStart = objectStarts.lastOrNull()
            when {
                escaped -> escaped = false
                inside && this[position] == '\\' -> escaped = true
                this[position] == '"' -> inside = !inside
                !inside && this[position] == '{' -> objectStarts.addLast(position)
                !inside && this[position] == '}' && objectStarts.isNotEmpty() -> {
                    val start = objectStarts.removeLast()
                    if (start == enclosingStart) return substring(start + 1, position)
                }
            }
        }
        return if (enclosingStart == null) substring(index) else ""
    }

    private fun String.isAtObjectTopLevel(index: Int): Boolean {
        var depth = 0
        var inside = false
        var escaped = false
        for (position in 0 until index) {
            when {
                escaped -> escaped = false
                inside && this[position] == '\\' -> escaped = true
                this[position] == '"' -> inside = !inside
                !inside && this[position] == '{' -> depth++
                !inside && this[position] == '}' -> depth--
            }
        }
        return !inside && depth == 0
    }

    private fun findRawBillingBalance(el: JsonElement): Double? = when (el) {
        is JsonObject -> {
            val customerId = el["customerID"] as? JsonPrimitive
            if (el.containsKey("balance") && customerId?.isString == true && customerId.content.isNotBlank()) {
                el["balance"]?.numberOrNull()
            } else el.values.firstNotNullOfOrNull { findRawBillingBalance(it) }
        }
        is JsonArray -> el.firstNotNullOfOrNull { findRawBillingBalance(it) }
        else -> null
    }

    private fun String.fallbackWindow(name: String, now: Instant): WindowValues? {
        val content = take(MAX_FALLBACK_LENGTH)
        val objectMatch = Regex(
            """\"?$name(?:Usage)?\"?\s*[:=]\s*(?:[^,{}$]|\s|[$]R\[\d+\]\s*=\s*)*[{]([^{}]{0,2048})[}]""",
            RegexOption.IGNORE_CASE
        ).find(content)?.groupValues?.get(1) ?: return null

        val percent = USAGE_PERCENT.find(objectMatch)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: USED_PERCENT.find(objectMatch)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: PERCENT_USED.find(objectMatch)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: runCatching {
                val usedMatch = USED_LIMIT.find(objectMatch)?.groupValues?.get(1)?.toDoubleOrNull()
                val limitMatch = USED_LIMIT.find(objectMatch)?.groupValues?.get(2)?.toDoubleOrNull()
                if (usedMatch != null && limitMatch != null && limitMatch > 0) {
                    (usedMatch / limitMatch).coerceIn(0.0, 1.0) * 100.0
                } else null
            }.getOrNull()
            ?: return null

        val rawReset = RESET_IN_SECONDS.find(objectMatch)?.groupValues?.get(1)?.toLongOrNull()
        if (rawReset != null && rawReset < 0) return null
        val resetInSeconds = rawReset?.takeIf { it >= 0 }
            ?: runCatching { RESET_IN_SECONDS_ALT.find(objectMatch)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it >= 0 } }.getOrNull()
        val resetAtInstant = RESET_AT_ISO.find(objectMatch)?.groupValues?.get(1)
            ?.toInstantOrNull()
            ?: RESETS_AT_ISO.find(objectMatch)?.groupValues?.get(1)
                ?.toInstantOrNull()
            ?: RESET_AT_EPOCH.find(objectMatch)?.groupValues?.get(1)?.let { raw ->
                val epoch = raw.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@let null
                val seconds = if (kotlin.math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD) epoch / 1000.0 else epoch
                runCatching { Instant.ofEpochSecond(seconds.toLong()) }.getOrNull()
            }
            ?: RESETS_AT_EPOCH.find(objectMatch)?.groupValues?.get(1)?.let { raw ->
                val epoch = raw.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@let null
                val seconds = if (kotlin.math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD) epoch / 1000.0 else epoch
                runCatching { Instant.ofEpochSecond(seconds.toLong()) }.getOrNull()
            }

        // Seroval hydration uses percent units even below 1%; JSON also accepts fractions.
        return WindowValues(percent, resetInSeconds, resetAtInstant, percentUnits = true)
    }

    private fun WindowValues.toUsageWindow(
        label: String,
        now: Instant,
        durationSeconds: Long?
    ): UsageWindow {
        val resetsAt = when {
            resetAtInstant != null -> resetAtInstant
            resetInSeconds != null -> runCatching { now.plusSeconds(resetInSeconds!!) }.getOrNull()
            else -> null
        }
        return UsageWindow(
            label = label,
            utilization = (if (!percentUnits && usagePercent <= 1.0) usagePercent else usagePercent / 100.0).coerceIn(0.0, 1.0),
            resetsAt = resetsAt,
            windowDurationSeconds = durationSeconds
        )
    }

    private fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)
        ?.content
        ?.toDoubleOrNull()
        ?.takeIf(Double::isFinite)

    private fun JsonElement.balanceNumberOrNull(): Double? = (this as? JsonPrimitive)
        ?.content
        ?.trim()
        ?.toFiniteBalanceNumberOrNull()

    private fun String.toFiniteBalanceNumberOrNull(): Double? {
        val normalized = when {
            JSON_NUMBER_REGEX.matches(this) -> this
            GROUPED_BALANCE_NUMBER_REGEX.matches(this) -> replace(",", "")
            else -> return null
        }
        return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private fun JsonElement.toInstantOrNull(): Instant? = (this as? JsonPrimitive)?.content?.toInstantOrNull()

    private fun String.toInstantOrNull(): Instant? {
        toDoubleOrNull()?.takeIf(Double::isFinite)?.let { epoch ->
            val seconds = if (kotlin.math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD) epoch / 1000.0 else epoch
            return runCatching { Instant.ofEpochSecond(seconds.toLong()) }.getOrNull()
        }
        return runCatching { Instant.parse(this) }.getOrNull()
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private data class WindowValues(
        val usagePercent: Double,
        val resetInSeconds: Long?,
        val resetAtInstant: Instant?,
        val percentUnits: Boolean = false
    )

    private val WINDOW_NAMES = listOf("rolling", "weekly", "monthly")
    private val USAGE_PERCENT = Regex("""\"?usagePercent\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val USED_PERCENT = Regex("""\"?usedPercent\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val PERCENT_USED = Regex("""\"?percentUsed\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val USED_LIMIT = Regex("""\"?used\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)\"?,\s*\"?limit\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val RESET_IN_SECONDS = Regex("""\"?resetInSec\"?\s*[:=]\s*\"?(-?\d+)""", RegexOption.IGNORE_CASE)
    private val RESET_IN_SECONDS_ALT = Regex("""\"?resetInSeconds\"?\s*[:=]\s*\"?(-?\d+)""", RegexOption.IGNORE_CASE)
    private val RESET_AT_ISO = Regex("""\"?resetAt\"?\s*[:=]\s*\"([^\"]+)\"""")
    private val RESETS_AT_ISO = Regex("""\"?resetsAt\"?\s*[:=]\s*\"([^\"]+)\"""")
    private val RESET_AT_EPOCH = Regex("""\"?resetAt\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""")
    private val RESETS_AT_EPOCH = Regex("""\"?resetsAt\"?\s*[:=]\s*\"?(-?\d+(?:\.\d+)?)""")
    private const val JSON_NUMBER = "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"
    private const val GROUPED_BALANCE_NUMBER = "-?[1-9]\\d{0,2}(?:,\\d{3})+(?:\\.\\d+)?"
    private const val BALANCE_NUMBER = "(?:$JSON_NUMBER|$GROUPED_BALANCE_NUMBER)"
    private const val BALANCE_TOKEN_END = "(?![A-Za-z0-9_,.+-])"
    private val JSON_NUMBER_REGEX = Regex(JSON_NUMBER)
    private val GROUPED_BALANCE_NUMBER_REGEX = Regex(GROUPED_BALANCE_NUMBER)
    private val BALANCE_LABELLED_HTML = Regex(
        """(?:Current\s+balance|Zen\s+balance|現在の残高)[^$]{0,80}[$]\s*($BALANCE_NUMBER)$BALANCE_TOKEN_END""",
        RegexOption.IGNORE_CASE
    )
    private const val FIVE_HOURS_SECONDS = 5L * 60L * 60L
    private const val WEEK_SECONDS = 7L * 24L * 60L * 60L
    private const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000.0
    private const val MAX_FALLBACK_LENGTH = 64 * 1024
    private const val MAX_DIAGNOSTIC_LENGTH = 256
    private const val MAX_DIAGNOSTIC_KEYS = 8
    private const val BILLING_SCALE = 100_000_000.0
    private val EXPLICIT_BALANCE_KEYS = setOf(
        "zenbalance",
        "zencurrentbalance",
        "currentbalance",
        "currentbalanceusd",
        "balanceusd",
        "usdbalance"
    )
    private val SAFE_DIAGNOSTIC_KEYS = setOf(
        "data",
        "displayName",
        "limit",
        "message",
        "monthlyUsage",
        "monthlyWindow",
        "payload",
        "percentUsed",
        "profile",
        "quota",
        "renewAt",
        "resetAt",
        "resetInSec",
        "resetInSeconds",
        "resetsAt",
        "rollingUsage",
        "rollingWindow",
        "status",
        "usage",
        "usagePercent",
        "used",
        "usedPercent",
        "weeklyUsage",
        "weeklyWindow"
    )
    private val BILLING_CUSTOMER_ID = Regex("""(?<![A-Za-z0-9_])(?:\"customerID\"|customerID)\s*:\s*(?:[$]R\[\d+\]\s*=\s*)?\"([^\"]+)\"""")
    private val BILLING_RAW_BALANCE = Regex("""(?<![A-Za-z0-9_])(?:\"balance\"|balance)\s*:\s*(?:[$]R\[\d+\]\s*=\s*)?(-?[0-9]+(?:\.[0-9]+)?)""")
    private val EXPLICIT_BALANCE_FALLBACK = Regex(
        """(?<![A-Za-z0-9_])(?:""" + EXPLICIT_BALANCE_KEYS.joinToString("|") + """)\s*=\s*($BALANCE_NUMBER)$BALANCE_TOKEN_END""",
        RegexOption.IGNORE_CASE
    )
}
