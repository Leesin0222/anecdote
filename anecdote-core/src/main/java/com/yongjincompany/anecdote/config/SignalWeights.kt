package com.yongjincompany.anecdote.config

/**
 * Scoring weights applied to each detection signal. Higher = more influence on the final
 * 0..100 score that drives the [com.yongjincompany.anecdote.BlockState] verdict.
 *
 * The defaults are tuned conservatively. Tune via [PolicyConfig.weights], typically by
 * `SignalWeights.DEFAULT.copy(...)`, when your app needs different sensitivity — e.g.,
 * a reward app that wants to weigh installed ad-blocker apps more heavily.
 *
 * @property probeDeltaWeight multiplier on `adFailureRate - controlFailureRate`. Primary
 *   signal. Range 0..1 × weight.
 * @property loadFailureWeight multiplier on average per-network load failure rate.
 * @property vpnBonus flat bonus when a VPN transport is active.
 * @property privateDnsBonus flat bonus when Android Private DNS is active (any provider).
 * @property knownAdBlockerDnsBonus additional flat bonus when the Private DNS server name
 *   matches the built-in or consumer-supplied ad-blocker DNS registry.
 * @property installedAdBlockerBonus flat bonus when at least one known ad-blocker package
 *   is installed on the device.
 */
public data class SignalWeights(
    public val probeDeltaWeight: Double = 80.0,
    public val loadFailureWeight: Double = 60.0,
    public val vpnBonus: Double = 10.0,
    public val privateDnsBonus: Double = 15.0,
    public val knownAdBlockerDnsBonus: Double = 60.0,
    public val installedAdBlockerBonus: Double = 50.0,
) {
    public companion object {
        public val DEFAULT: SignalWeights = SignalWeights()
    }
}
