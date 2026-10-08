package com.codexbar.android.core.nativecli

import com.codexbar.android.core.domain.model.*
import java.time.Instant
import kotlinx.serialization.json.*

/** Optional enrichment is independent of usable quota windows. Never interpret unread zero as balance. */
internal object CodexReportedCredits {
    fun balance(value: JsonElement?): ReportedCredits? = runCatching {
        val data = value?.takeUnless { it == JsonNull }?.jsonObject ?: return null
        val cap = runCatching {
            val limit = data["codexCreditLimit"]?.takeUnless { it == JsonNull }?.jsonObject ?: return@runCatching null
            CreditCap(limit.amount("used"), limit.amount("limit"), limit.amount("remaining"),
                limit.dateOrNull("resetsAt"), limit.date("updatedAt"))
        }.getOrNull()
        ReportedCredits(if (data["balanceReadSucceeded"]?.jsonPrimitive?.booleanOrNull == false) null else data.amount("remaining"),
            data["balanceIsWorkspace"]?.jsonPrimitive?.booleanOrNull == true, cap, data.date("updatedAt"))
    }.getOrNull()

    fun inventory(value: JsonElement?): ResetInventory? = runCatching {
        val data = value?.takeUnless { it == JsonNull }?.jsonObject ?: return null
        val count = data.getValue("availableCount").jsonPrimitive.int
        require(count >= 0)
        val items = data.getValue("credits").jsonArray
        require(items.size <= 100)
        ResetInventory(count, items.map { item ->
            val credit = item.jsonObject
            ResetCredit(credit.text("reset_type"), credit.text("status"), credit.dateOrNull("expires_at"))
        }, data.date("updatedAt"))
    }.getOrNull()

    private fun JsonObject.amount(key: String) = getValue(key).jsonPrimitive.double.also { require(it.isFinite() && it >= 0) }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content.also {
        require(it.length in 1..128 && it.none(Char::isISOControl))
    }
    private fun JsonObject.date(key: String) = Instant.parse(getValue(key).jsonPrimitive.content)
    private fun JsonObject.dateOrNull(key: String) = get(key)?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let(Instant::parse)
}
