package com.yongjincompany.anecdote

/**
 * Per-signal contributions behind a [BlockState.Suspected] / [BlockState.Blocked] verdict.
 * Report it alongside the state to see *why* a user was flagged and to tune
 * [com.yongjincompany.anecdote.config.SignalWeights] from field data.
 *
 * Direct-evidence fields are the points actually added. Ambient fields are the raw points
 * before [ambientMultiplier] is applied (1.0 normally, the configured discount when ads were
 * demonstrably reachable).
 *
 * @property total final 0..100 score compared against the policy thresholds.
 */
public data class ScoreBreakdown(
    val total: Int,
    val probeDelta: Double,
    val dnsBlock: Double,
    val loadFailure: Double,
    val vpn: Double,
    val privateDns: Double,
    val knownAdBlockerDns: Double,
    val installedAdBlocker: Double,
    val ambientMultiplier: Double,
) {
    /** Sum of ambient points after [ambientMultiplier]. */
    val ambientApplied: Double
        get() = (vpn + privateDns + knownAdBlockerDns + installedAdBlocker) * ambientMultiplier

    /** Sum of direct-evidence points. */
    val direct: Double get() = probeDelta + dnsBlock + loadFailure
}
