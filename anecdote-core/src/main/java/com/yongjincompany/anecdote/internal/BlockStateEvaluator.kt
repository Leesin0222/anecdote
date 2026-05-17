package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.config.PolicyConfig
import com.yongjincompany.anecdote.config.SignalWeights

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
        val hasKnownDns = snapshot.environment?.let {
            it.privateDnsActive && matchesKnownAdBlockerDns(it.privateDnsServer)
        } == true

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
        var score = 0.0

        val probeDelta = snapshot.probe.delta.coerceAtLeast(0.0)
        score += probeDelta * weights.probeDeltaWeight

        val avgLoadFailureRate = if (snapshot.networks.isEmpty()) {
            0.0
        } else {
            snapshot.networks.values.map { it.loadFailureRate }.average()
        }
        score += avgLoadFailureRate * weights.loadFailureWeight

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

        return score.toInt().coerceIn(0, 100)
    }

    private fun computeConfidence(snapshot: AggregateSnapshot): Confidence {
        val sampleSize = snapshot.probe.adAttempts +
            snapshot.probe.controlAttempts +
            snapshot.totalLoadAttempts
        val env = snapshot.environment
        val hasStrongAmbientSignal = snapshot.installedAdBlockerPackages.isNotEmpty() ||
            (env?.privateDnsActive == true && matchesKnownAdBlockerDns(env.privateDnsServer))
        return when {
            // A strong ambient signal alone is enough for HIGH confidence even without probe data.
            hasStrongAmbientSignal && sampleSize < THRESHOLD_MEDIUM -> Confidence.HIGH
            sampleSize < THRESHOLD_LOW -> Confidence.LOW
            sampleSize < THRESHOLD_MEDIUM -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }
    }

    private fun matchesKnownAdBlockerDns(server: String?): Boolean =
        AdBlockerDnsRegistry.looksLikeAdBlocker(server, extraAdBlockerDnsSuffixes)

    private companion object {
        const val THRESHOLD_LOW = 5
        const val THRESHOLD_MEDIUM = 20
    }
}
