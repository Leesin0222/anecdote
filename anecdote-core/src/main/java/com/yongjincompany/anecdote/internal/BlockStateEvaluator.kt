package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.config.PolicyConfig
import com.yongjincompany.anecdote.config.SignalWeights

/**
 * Turns an [AggregateSnapshot] into a [BlockState].
 *
 * Signals fall into two tiers:
 * - **Direct evidence** — probe results (ad vs control domains) and ad load outcomes. These
 *   observe whether ad traffic actually gets through.
 * - **Ambient signals** — VPN, Private DNS, known ad-blocker DNS, installed blocker apps.
 *   These describe the environment but don't prove blocking is active.
 *
 * When direct evidence shows ads are reachable, ambient signals are discounted so they can't
 * outvote it. Without direct evidence, ambient signals still produce a verdict but confidence
 * is capped at MEDIUM so the session isn't locked by a circumstantial call.
 */
internal class BlockStateEvaluator(
    private val policy: PolicyConfig,
    private val extraAdBlockerDnsSuffixes: Set<String> = emptySet(),
) {
    private val weights: SignalWeights = policy.weights

    fun evaluate(snapshot: AggregateSnapshot): BlockState {
        snapshot.environment?.mcc?.let { mcc ->
            if (mcc in policy.disabledRegionMccs) return BlockState.Unknown
        }

        val hasProbeData = snapshot.probe.adAttempts > 0
        val hasLoadData = snapshot.totalLoadAttempts > 0
        val hasInstalledBlocker = snapshot.installedAdBlockerPackages.isNotEmpty()
        val hasKnownDns = hasKnownAdBlockerDns(snapshot)

        if (!hasProbeData && !hasLoadData && !hasInstalledBlocker && !hasKnownDns) {
            return BlockState.Unknown
        }

        val score = computeScore(snapshot)
        val confidence = computeConfidence(snapshot)

        return when {
            score >= policy.thresholdBlocked -> BlockState.Blocked(confidence, snapshot.recentSignals)
            score >= policy.thresholdSuspected -> BlockState.Suspected(confidence, snapshot.recentSignals)
            else -> BlockState.NotBlocked
        }
    }

    internal fun computeScore(snapshot: AggregateSnapshot): Int {
        val direct = probeScore(snapshot.probe) + loadScore(snapshot)
        val ambientRaw = ambientScore(snapshot)
        val ambient = if (adsReachable(snapshot.probe)) {
            ambientRaw * weights.ambientDiscountWhenAdsReachable
        } else {
            ambientRaw
        }
        return (direct + ambient).toInt().coerceIn(0, MAX_SCORE)
    }

    private fun probeScore(probe: ProbeStats): Double {
        // Without any control probe we can't tell "ads blocked" from "device offline".
        if (probe.controlAttempts == 0 || probe.adAttempts == 0) return 0.0
        val delta = probe.delta.coerceAtLeast(0.0)
        return delta * weights.probeDeltaWeight * probeSampleFactor(probe)
    }

    /** 0..1 — how close the probe window is to the configured minimum sample sizes. */
    private fun probeSampleFactor(probe: ProbeStats): Double {
        val adFactor = (probe.adAttempts.toDouble() / policy.minAdProbeSamples).coerceAtMost(1.0)
        val ctrlFactor = (probe.controlAttempts.toDouble() / policy.minControlProbeSamples).coerceAtMost(1.0)
        return adFactor * ctrlFactor
    }

    private fun hasSufficientProbeSamples(probe: ProbeStats): Boolean =
        probe.adAttempts >= policy.minAdProbeSamples &&
            probe.controlAttempts >= policy.minControlProbeSamples

    private fun adsReachable(probe: ProbeStats): Boolean =
        hasSufficientProbeSamples(probe) && probe.adFailureRate <= ADS_REACHABLE_MAX_FAILURE_RATE

    private fun loadScore(snapshot: AggregateSnapshot): Double {
        if (snapshot.networks.isEmpty()) return 0.0
        val avgLoadFailureRate = snapshot.networks.values.map { it.loadFailureRate }.average()
        return avgLoadFailureRate * weights.loadFailureWeight
    }

    private fun ambientScore(snapshot: AggregateSnapshot): Double {
        var score = 0.0
        snapshot.environment?.let { env ->
            if (env.vpnActive) score += weights.vpnBonus
            if (env.privateDnsActive) {
                score += weights.privateDnsBonus
                if (matchesKnownAdBlockerDns(env.privateDnsServer)) {
                    score += weights.knownAdBlockerDnsBonus
                }
            }
        }
        if (snapshot.installedAdBlockerPackages.isNotEmpty()) {
            score += weights.installedAdBlockerBonus
        }
        return score
    }

    private fun computeConfidence(snapshot: AggregateSnapshot): Confidence {
        val sampleSize = snapshot.probe.adAttempts +
            snapshot.probe.controlAttempts +
            snapshot.totalLoadAttempts
        val hasStrongAmbientSignal = snapshot.installedAdBlockerPackages.isNotEmpty() ||
            hasKnownAdBlockerDns(snapshot)

        if (sampleSize == 0) {
            // Ambient-only verdict: plausible but unproven. Cap at MEDIUM so it never locks
            // the session (DetectorGate only locks on HIGH).
            return if (hasStrongAmbientSignal) Confidence.MEDIUM else Confidence.LOW
        }

        val base = when {
            sampleSize < THRESHOLD_LOW -> Confidence.LOW
            sampleSize < THRESHOLD_MEDIUM -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }
        // A strong ambient signal that agrees with some direct evidence lifts confidence a step.
        return if (hasStrongAmbientSignal) base.stepUp() else base
    }

    private fun Confidence.stepUp(): Confidence = when (this) {
        Confidence.LOW -> Confidence.MEDIUM
        Confidence.MEDIUM, Confidence.HIGH -> Confidence.HIGH
    }

    private fun hasKnownAdBlockerDns(snapshot: AggregateSnapshot): Boolean =
        snapshot.environment?.let {
            it.privateDnsActive && matchesKnownAdBlockerDns(it.privateDnsServer)
        } == true

    private fun matchesKnownAdBlockerDns(server: String?): Boolean =
        AdBlockerDnsRegistry.looksLikeAdBlocker(server, extraAdBlockerDnsSuffixes)

    private companion object {
        const val THRESHOLD_LOW = 5
        const val THRESHOLD_MEDIUM = 20
        const val MAX_SCORE = 100

        /** Ad-domain failure rate at or below which ads are considered reachable. */
        const val ADS_REACHABLE_MAX_FAILURE_RATE = 0.2
    }
}
