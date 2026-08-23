package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.ProviderCategory
import com.codexbar.android.core.domain.model.providerMetadata
import com.codexbar.android.core.security.ConnectionHealth
import java.util.Locale

internal enum class ProviderConnectionFilter {
    ALL,
    CONNECTED,
    NOT_CONNECTED
}

internal enum class ProviderCategoryFilter(val category: ProviderCategory?) {
    ALL(null),
    CODING(ProviderCategory.CODING),
    MODEL_API(ProviderCategory.MODEL_API),
    ROUTER(ProviderCategory.ROUTER),
    MEDIA(ProviderCategory.MEDIA)
}

internal fun filterProviders(
    query: String,
    filter: ProviderConnectionFilter,
    categoryFilter: ProviderCategoryFilter,
    serviceStates: Map<AiService, ServiceCredentialState>
): List<AiService> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)

    return AiService.entries
        .asSequence()
        .filter { service ->
            val isConnected = serviceStates[service]?.isVerifiedConnected == true
            when (filter) {
                ProviderConnectionFilter.ALL -> true
                ProviderConnectionFilter.CONNECTED -> isConnected
                ProviderConnectionFilter.NOT_CONNECTED -> !isConnected
            }
        }
        .filter { service ->
            categoryFilter.category == null ||
                service.providerMetadata.category == categoryFilter.category
        }
        .filter { service ->
            if (normalizedQuery.isEmpty()) return@filter true
            buildList {
                add(service.displayName)
                add(service.name)
                addAll(service.providerMetadata.aliases)
            }.any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
        }
        .sortedWith(
            compareByDescending<AiService> { serviceStates[it]?.isVerifiedConnected == true }
                .thenByDescending { serviceStates[it]?.isConnected == true }
                .thenByDescending { it.providerMetadata.recommended }
                .thenBy { it.ordinal }
        )
        .toList()
}

internal val ServiceCredentialState.isVerifiedConnected: Boolean
    get() = isConnected && connectionHealth == ConnectionHealth.CONNECTED
