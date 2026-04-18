package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence

internal class DetectorGate(
    private val gracePeriodMs: Long,
    private val maxEvaluationsPerSession: Int,
) {
    private var startedAt: Long = 0L
    private var evaluationCount: Int = 0
    private var confirmedHighBlocked: Boolean = false
    private var lastEnvSignature: EnvSignature? = null

    @Synchronized
    fun onStart(now: Long) {
        startedAt = now
        evaluationCount = 0
        confirmedHighBlocked = false
        lastEnvSignature = null
    }

    @Synchronized
    fun shouldEvaluate(now: Long, environment: EnvironmentStats?): Boolean {
        applyEnvironmentChange(now, environment)
        if (now - startedAt < gracePeriodMs) return false
        if (confirmedHighBlocked) return false
        if (evaluationCount >= maxEvaluationsPerSession) return false
        return true
    }

    @Synchronized
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
