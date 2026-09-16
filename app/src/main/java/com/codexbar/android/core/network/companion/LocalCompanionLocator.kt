package com.codexbar.android.core.network.companion

import android.util.Log
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds companion candidates again after the computer running it received a new DHCP address.
 *
 * Only the subnet the device is already attached to is probed, and only with a TCP connect on the
 * paired port: nothing is sent until the caller authenticates the host with the stored pairing
 * key, so a wrong candidate never receives companion traffic.
 */
@Singleton
open class LocalCompanionLocator @Inject constructor() {

    open suspend fun reachableHosts(
        port: Int,
        previousHost: String?,
        limit: Int = DEFAULT_HOST_LIMIT
    ): List<String> = withContext(Dispatchers.IO) {
        if (port !in 1..65535 || limit <= 0) return@withContext emptyList()
        val candidates = localInterfaces()
            .flatMap { (address, prefixLength) ->
                LocalSubnetScanPlan.candidates(
                    localAddress = address,
                    prefixLength = prefixLength,
                    previousHost = previousHost
                )
            }
            .distinct()
        if (candidates.isEmpty()) return@withContext emptyList()

        val gate = Semaphore(MAX_PARALLEL_PROBES)
        val reachable = withTimeoutOrNull(SCAN_BUDGET_MILLIS) {
            coroutineScope {
                candidates.map { host ->
                    async { host.takeIf { gate.withPermit { isPortOpen(it, port) } } }
                }.awaitAll()
            }
        }
        reachable.orEmpty().filterNotNull().take(limit)
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS)
                true
            }
        }.getOrDefault(false)
    }

    /** Returns the device's own private IPv4 addresses with their prefix length. */
    private fun localInterfaces(): List<Pair<String, Int>> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                .orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { networkInterface -> networkInterface.interfaceAddresses.asSequence() }
                .mapNotNull { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
                    if (!address.isSiteLocalAddress) return@mapNotNull null
                    address.hostAddress?.let { it to interfaceAddress.networkPrefixLength.toInt() }
                }
                .distinct()
                .toList()
        }.getOrElse { error ->
            Log.w(TAG, "Could not enumerate local interfaces", error)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CodexBarCompanion"
        const val PROBE_TIMEOUT_MILLIS = 350
        const val MAX_PARALLEL_PROBES = 24
        const val SCAN_BUDGET_MILLIS = 8_000L
        const val DEFAULT_HOST_LIMIT = 4
    }
}
