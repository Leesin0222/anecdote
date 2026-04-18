package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorGateTest {

    private fun gate(
        gracePeriodMs: Long = 5_000L,
        maxEvaluationsPerSession: Int = 3,
    ) = DetectorGate(gracePeriodMs, maxEvaluationsPerSession)

    private fun env(vpn: Boolean = false, privateDns: Boolean = false) = EnvironmentStats(
        vpnActive = vpn,
        privateDnsActive = privateDns,
        privateDnsServer = null,
        mcc = null,
    )

    @Test
    fun `evaluation blocked during grace period`() {
        val g = gate(gracePeriodMs = 5_000L)
        g.onStart(now = 0L)

        assertFalse(g.shouldEvaluate(now = 0L, environment = null))
        assertFalse(g.shouldEvaluate(now = 4_999L, environment = null))
        assertTrue(g.shouldEvaluate(now = 5_000L, environment = null))
    }

    @Test
    fun `session limit stops evaluation after N transitions`() {
        val g = gate(gracePeriodMs = 0L, maxEvaluationsPerSession = 3)
        g.onStart(now = 0L)

        assertTrue(g.shouldEvaluate(now = 1L, environment = null))
        g.onStateTransitioned(BlockState.NotBlocked)

        assertTrue(g.shouldEvaluate(now = 2L, environment = null))
        g.onStateTransitioned(BlockState.Suspected(Confidence.LOW, emptyList()))

        assertTrue(g.shouldEvaluate(now = 3L, environment = null))
        g.onStateTransitioned(BlockState.Suspected(Confidence.MEDIUM, emptyList()))

        // After 3 transitions, next evaluation should be blocked
        assertFalse(g.shouldEvaluate(now = 4L, environment = null))
    }

    @Test
    fun `HIGH confidence Blocked locks further evaluation`() {
        val g = gate(gracePeriodMs = 0L, maxEvaluationsPerSession = 100)
        g.onStart(now = 0L)

        assertTrue(g.shouldEvaluate(now = 1L, environment = null))
        g.onStateTransitioned(BlockState.Blocked(Confidence.HIGH, emptyList()))

        assertFalse(g.shouldEvaluate(now = 2L, environment = null))
        assertFalse(g.shouldEvaluate(now = 10_000L, environment = null))
    }

    @Test
    fun `LOW or MEDIUM Blocked does NOT lock further evaluation`() {
        val g = gate(gracePeriodMs = 0L, maxEvaluationsPerSession = 100)
        g.onStart(now = 0L)

        g.onStateTransitioned(BlockState.Blocked(Confidence.MEDIUM, emptyList()))
        assertTrue(g.shouldEvaluate(now = 2L, environment = null))
    }

    @Test
    fun `environment change resets grace period and session state`() {
        val g = gate(gracePeriodMs = 5_000L, maxEvaluationsPerSession = 2)
        g.onStart(now = 0L)

        // Get past grace
        assertTrue(g.shouldEvaluate(now = 5_000L, environment = env(vpn = false)))
        g.onStateTransitioned(BlockState.NotBlocked)

        assertTrue(g.shouldEvaluate(now = 6_000L, environment = env(vpn = false)))
        g.onStateTransitioned(BlockState.Blocked(Confidence.HIGH, emptyList()))

        // Locked
        assertFalse(g.shouldEvaluate(now = 7_000L, environment = env(vpn = false)))

        // Network changed: VPN turned on. Reset should happen.
        // First call after env change enters grace period (starts at now = 8_000L)
        assertFalse(g.shouldEvaluate(now = 8_000L, environment = env(vpn = true)))
        // Still in grace
        assertFalse(g.shouldEvaluate(now = 12_999L, environment = env(vpn = true)))
        // Past grace → can evaluate again
        assertTrue(g.shouldEvaluate(now = 13_000L, environment = env(vpn = true)))
    }

    @Test
    fun `same environment does not trigger reset`() {
        val g = gate(gracePeriodMs = 1_000L, maxEvaluationsPerSession = 1)
        g.onStart(now = 0L)

        val sameEnv = env(vpn = true, privateDns = false)
        assertTrue(g.shouldEvaluate(now = 1_000L, environment = sameEnv))
        g.onStateTransitioned(BlockState.NotBlocked)

        // Same env, session limit exhausted
        assertFalse(g.shouldEvaluate(now = 1_500L, environment = sameEnv))
    }

    @Test
    fun `null environment does not trigger reset`() {
        val g = gate(gracePeriodMs = 0L, maxEvaluationsPerSession = 1)
        g.onStart(now = 0L)

        assertTrue(g.shouldEvaluate(now = 1L, environment = null))
        g.onStateTransitioned(BlockState.NotBlocked)

        // Transition to an env from null: this is first observation, no reset
        assertFalse(g.shouldEvaluate(now = 2L, environment = env(vpn = true)))
    }

    @Test
    fun `onStart resets all gates`() {
        val g = gate(gracePeriodMs = 5_000L, maxEvaluationsPerSession = 1)
        g.onStart(now = 0L)

        // Exhaust
        assertTrue(g.shouldEvaluate(now = 5_000L, environment = null))
        g.onStateTransitioned(BlockState.Blocked(Confidence.HIGH, emptyList()))
        assertFalse(g.shouldEvaluate(now = 6_000L, environment = null))

        // Restart
        g.onStart(now = 10_000L)
        assertFalse(g.shouldEvaluate(now = 10_000L, environment = null)) // in grace
        assertTrue(g.shouldEvaluate(now = 15_000L, environment = null))
    }
}
