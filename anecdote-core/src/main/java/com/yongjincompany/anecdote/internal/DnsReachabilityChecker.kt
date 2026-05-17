package com.yongjincompany.anecdote.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.UnknownHostException

internal data class DnsResolution(
    /** True when at least one returned address is a real, routable destination. */
    val resolved: Boolean,
    /** True when at least one address came back but every one of them is a sinkhole IP. */
    val allSinkholed: Boolean,
    /** True when the lookup completed without throwing (regardless of sinkhole status). */
    val lookupSucceeded: Boolean,
)

internal fun interface DnsReachabilityChecker {
    suspend fun resolve(host: String): DnsResolution
}

internal class InetAddressDnsChecker(
    private val timeoutMs: Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DnsReachabilityChecker {

    override suspend fun resolve(host: String): DnsResolution = withContext(dispatcher) {
        val lookup = withTimeoutOrNull(timeoutMs) {
            runCatching { InetAddress.getAllByName(host).toList() }
        }
        when {
            lookup == null -> DnsResolution(
                resolved = false,
                allSinkholed = false,
                lookupSucceeded = false,
            )
            lookup.isFailure -> {
                // UnknownHostException = NXDOMAIN / DNS-level block. Other errors treated the same.
                val cause = lookup.exceptionOrNull()
                val isNxdomain = cause is UnknownHostException
                DnsResolution(
                    resolved = false,
                    allSinkholed = false,
                    lookupSucceeded = isNxdomain,
                )
            }
            else -> {
                val addrs = lookup.getOrNull().orEmpty()
                if (addrs.isEmpty()) {
                    DnsResolution(resolved = false, allSinkholed = false, lookupSucceeded = true)
                } else {
                    val allSinkholed = addrs.all { it.isSinkholeAddress() }
                    DnsResolution(
                        resolved = !allSinkholed,
                        allSinkholed = allSinkholed,
                        lookupSucceeded = true,
                    )
                }
            }
        }
    }
}

internal fun InetAddress.isSinkholeAddress(): Boolean =
    isAnyLocalAddress || isLoopbackAddress
