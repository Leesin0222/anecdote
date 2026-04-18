package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence

/**
 * Encapsulates evaluation gating rules for [com.yongjincompany.anecdote.AdBlockDetector]:
 *
 * - **Grace period**: during the first `gracePeriodMs` after [onStart] (or after a network
 *   environment change), evaluation is suppressed so that cold-start transients don't
 *   produce false positives.
 * - **Session limit**: at most `maxEvaluationsPerSession` state transitions are accepted
 *   before re-evaluation stops, preventing runaway flapping.
 * - **High-confidence lock**: once a [BlockState.Blocked] with [Confidence.HIGH] is
 *   confirmed, re-evaluation stops unless the network environment changes.
 * - **Network change reset**: a change in VPN / Private DNS state resets the session
 *   (new grace period, count, and confirmation lock).
 */
internal class DetectorGate(
    private val gracePeriodMs: Long,
    private val maxEvaluationsPerSession: Int,
) {
    private var startedAt: Long = 0L
    private var evaluationCount: Int = 0
    private var confirmedHighBlocked: Boolean = false
    private var lastEnvSignature: EnvSignature? = null

    fun onStart(now: Long) {
        startedAt = now
        evaluationCount = 0
        confirmedHighBlocked = false
        lastEnvSignature = null
    }

    /**
     * @return `true` if evaluator should be run at [now] given [environment]; `false` if
     * the result should be skipped (state remains whatever it was).
     */
    fun shouldEvaluate(now: Long, environment: EnvironmentStats?): Boolean {
        applyEnvironmentChange(now, environment)
        if (now - startedAt < gracePeriodMs) return false
        if (confirmedHighBlocked) return false
        if (evaluationCount >= maxEvaluationsPerSession) return false
        return true
    }

    fun onStateTransitioned(newState: BlockState) {
        evaluationCount++
        if (newState is BlockState.Blocked && newState.confidence == Confidence.HIGH) {
            confirmedHighBlocked = true
        }
    }

    private fun applyEnvironmentChange(now: Long, environment: EnvironmentStats?) {
        val newSig = environment?.let {
            EnvSignature(vpnActive = it.vpnActive, privateDnsActive = it.privateDnsActive)
        }
        val previousSig = lastEnvSignature
        lastEnvSignature = newSig
        if (previousSig != null && newSig != null && previousSig != newSig) {
            startedAt = now
            evaluationCount = 0
            confirmedHighBlocked = false
        }
    }

    internal data class EnvSignature(
        val vpnActive: Boolean,
        val privateDnsActive: Boolean,
    )
}
