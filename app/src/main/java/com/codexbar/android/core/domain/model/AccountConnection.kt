package com.codexbar.android.core.domain.model

import java.util.UUID

/** Local identity; a provider account ID or an email is never a storage key. */
data class AccountConnection(
    val id: String,
    val service: AiService,
    val name: String,
    val generation: String
) {
    init {
        require(id == service.name || canonicalUuid(id)) { "Invalid connection ID" }
        require(canonicalUuid(generation)) { "Invalid connection generation" }
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl)) {
            "Account name must contain 1 to 80 printable characters"
        }
    }

    fun reconnect(): AccountConnection = copy(generation = UUID.randomUUID().toString())

    companion object {
        fun create(service: AiService, name: String = service.displayName): AccountConnection =
            AccountConnection(UUID.randomUUID().toString(), service, name.trim(), UUID.randomUUID().toString())

        private fun canonicalUuid(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }
}
