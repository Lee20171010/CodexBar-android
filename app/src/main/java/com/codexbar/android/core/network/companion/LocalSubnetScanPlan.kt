package com.codexbar.android.core.network.companion

/**
 * Builds the ordered address list used to find a companion again after its DHCP lease changed.
 *
 * The plan never leaves the subnet the device is already attached to, and it is bounded to a
 * single /24 so a wide home or office prefix cannot turn a reconnect into a network-wide sweep.
 */
internal object LocalSubnetScanPlan {
    const val MAX_CANDIDATES = 254
    private const val NARROWEST_PREFIX_LENGTH = 24
    private const val WIDEST_SUPPORTED_PREFIX_LENGTH = 30

    fun candidates(
        localAddress: String,
        prefixLength: Int,
        previousHost: String?,
        excluding: Set<String> = emptySet()
    ): List<String> {
        val localOctets = octetsOrNull(localAddress) ?: return emptyList()
        if (prefixLength !in 1..WIDEST_SUPPORTED_PREFIX_LENGTH) return emptyList()

        val effectivePrefix = maxOf(prefixLength, NARROWEST_PREFIX_LENGTH)
        val hostBits = 32 - effectivePrefix
        val mask = (-1L shl hostBits) and 0xFFFFFFFFL
        val localValue = localOctets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
        val network = localValue and mask
        val lastHost = (1L shl hostBits) - 2
        if (lastHost < 1) return emptyList()

        val skipped = excluding + localAddress
        val reference = previousHost
            ?.let { octetsOrNull(it) }
            ?.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
            ?.takeIf { (it and mask) == network }

        return (1..lastHost)
            .asSequence()
            .map { host -> network or host }
            .filter { value -> reference == null || value != reference }
            .sortedBy { value -> reference?.let { kotlin.math.abs(value - it) } ?: 0L }
            .map { value -> value.toDottedQuad() }
            .filterNot { it in skipped }
            .take(MAX_CANDIDATES)
            .toList()
    }

    private fun octetsOrNull(address: String): List<Int>? {
        val parts = address.trim().split('.')
        if (parts.size != 4) return null
        return parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
    }

    private fun Long.toDottedQuad(): String {
        return listOf(24, 16, 8, 0).joinToString(".") { shift -> ((this shr shift) and 0xFF).toString() }
    }
}
