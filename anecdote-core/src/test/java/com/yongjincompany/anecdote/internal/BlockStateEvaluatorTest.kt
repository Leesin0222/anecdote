package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.config.PolicyConfig
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStateEvaluatorTest {

    private val defaultPolicy: PolicyConfig = PolicyConfig.Builder().build()

    @Test
    fun `empty snapshot returns Unknown`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val result = evaluator.evaluate(AggregateSnapshot.EMPTY)
        assertEquals(BlockState.Unknown, result)
    }

    @Test
    fun `disabled region MCC returns Unknown regardless of signals`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 10, adFailures = 10, adFailureRate = 1.0,
                controlAttempts = 10, controlFailures = 0, controlFailureRate = 0.0),
            environment = EnvironmentStats(false, false, null, mcc = 460),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.Unknown, result)
    }

    @Test
    fun `all probes reachable yields NotBlocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 10, adFailures = 0, adFailureRate = 0.0,
                controlAttempts = 10, controlFailures = 0, controlFailureRate = 0.0),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `ad failures with control success yields Blocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 10, adFailures = 10, adFailureRate = 1.0,
                controlAttempts = 10, controlFailures = 0, controlFailureRate = 0.0),
            recentSignals = listOf(sampleProbe()),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Blocked, got $result", result is BlockState.Blocked)
    }

    @Test
    fun `ad and control both fail yields NotBlocked (network failure)`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 10, adFailures = 10, adFailureRate = 1.0,
                controlAttempts = 10, controlFailures = 10, controlFailureRate = 1.0),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `moderate ad failure with VPN bonus pushes to Suspected`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // ad delta 0.4 × 80 = 32, + vpn 10 = 42 → Suspected (threshold 40)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 10, adFailures = 4, adFailureRate = 0.4,
                controlAttempts = 10, controlFailures = 0, controlFailureRate = 0.0),
            environment = EnvironmentStats(vpnActive = true, privateDnsActive = false,
                privateDnsServer = null, mcc = null),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Suspected, got $result", result is BlockState.Suspected)
    }

    @Test
    fun `confidence low for small sample size`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 2, adFailures = 2, adFailureRate = 1.0,
                controlAttempts = 1, controlFailures = 0, controlFailureRate = 0.0),
        )
        val result = evaluator.evaluate(snap)
        assertNotNull(result)
        val confidence = when (result) {
            is BlockState.Blocked -> result.confidence
            is BlockState.Suspected -> result.confidence
            else -> error("expected Blocked or Suspected, got $result")
        }
        assertEquals(Confidence.LOW, confidence)
    }

    @Test
    fun `confidence high for large sample size`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(adAttempts = 30, adFailures = 30, adFailureRate = 1.0,
                controlAttempts = 30, controlFailures = 0, controlFailureRate = 0.0),
        )
        val result = evaluator.evaluate(snap) as BlockState.Blocked
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `load failure contributes even without probe data`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // all load failures, weight 60 → 60 points = Suspected (40) but below Blocked (70)
        val snap = AggregateSnapshot.EMPTY.copy(
            networks = mapOf(
                "admob" to NetworkStats("admob", loadAttempts = 10, loadFailures = 10, loadFailureRate = 1.0),
            ),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Suspected, got $result", result is BlockState.Suspected)
    }

    private fun sampleProbe() = AdNetworkSignal.ProbeResult(
        networkId = "probe",
        timestamp = 1_000L,
        domain = "ad.example",
        isControl = false,
        reachable = false,
        latencyMs = null,
    )
}
