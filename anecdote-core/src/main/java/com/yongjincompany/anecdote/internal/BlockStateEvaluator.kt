package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.config.PolicyConfig

internal data class SignalWeights(
    val probeDeltaWeight: Double,
    val loadFailureWeight: Double,
    val vpnBonus: Double,
    val privateDnsBonus: Double,
) {
    companion object {
        val DEFAULT = SignalWeights(
            probeDeltaWeight = 80.0,
            loadFailureWeight = 60.0,
            vpnBonus = 10.0,
            privateDnsBonus = 15.0,
        )
    }
}

internal class BlockStateEvaluator(
    private val policy: PolicyConfig,
    private val weights: SignalWeights = SignalWeights.DEFAULT,
) {

    fun evaluate(snapshot: AggregateSnapshot): BlockState {
        snapshot.environment?.mcc?.let { mcc ->
            if (mcc in policy.disabledRegionMccs) return BlockState.Unknown
        }

        val hasProbeData = snapshot.probe.adAttempts > 0
        val hasLoadData = snapshot.totalLoadAttempts > 0
        if (!hasProbeData && !hasLoadData) return BlockState.Unknown

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
            if (env.privateDnsActive) score += weights.privateDnsBonus
        }

        return score.toInt().coerceIn(0, 100)
    }

    private fun computeConfidence(snapshot: AggregateSnapshot): Confidence {
        val sampleSize = snapshot.probe.adAttempts +
            snapshot.probe.controlAttempts +
            snapshot.totalLoadAttempts
        return when {
            sampleSize < THRESHOLD_LOW -> Confidence.LOW
            sampleSize < THRESHOLD_MEDIUM -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }
    }

    private companion object {
        const val THRESHOLD_LOW = 5
        const val THRESHOLD_MEDIUM = 20
    }
}
