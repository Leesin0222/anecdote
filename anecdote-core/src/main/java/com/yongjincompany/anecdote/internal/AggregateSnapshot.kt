package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.AdNetworkSignal

internal data class NetworkStats(
    val networkId: String,
    val loadAttempts: Int,
    val loadFailures: Int,
    val loadFailureRate: Double,
)

internal data class ProbeStats(
    val adAttempts: Int,
    val adFailures: Int,
    val adFailureRate: Double,
    val controlAttempts: Int,
    val controlFailures: Int,
    val controlFailureRate: Double,
    /** Ad-domain probes answered with a sinkhole address. */
    val adDnsSinkholed: Int = 0,
    /** Ad-domain probes answered NXDOMAIN. */
    val adDnsNxdomain: Int = 0,
    /** Control-domain probes answered with a sinkhole or NXDOMAIN — resolver itself is broken. */
    val controlDnsBlocked: Int = 0,
) {
    /** Positive = ad domains fail more than control. Primary blocking signal. */
    val delta: Double get() = adFailureRate - controlFailureRate
}

internal data class EnvironmentStats(
    val vpnActive: Boolean,
    val privateDnsActive: Boolean,
    val privateDnsServer: String?,
    val mcc: Int?,
)

internal data class AggregateSnapshot(
    val networks: Map<String, NetworkStats>,
    val probe: ProbeStats,
    val environment: EnvironmentStats?,
    val installedAdBlockerPackages: Set<String>,
    val recentSignals: List<AdNetworkSignal>,
) {
    val totalLoadAttempts: Int get() = networks.values.sumOf { it.loadAttempts }

    companion object {
        val EMPTY = AggregateSnapshot(
            networks = emptyMap(),
            probe = ProbeStats(0, 0, 0.0, 0, 0, 0.0),
            environment = null,
            installedAdBlockerPackages = emptySet(),
            recentSignals = emptyList(),
        )
    }
}
