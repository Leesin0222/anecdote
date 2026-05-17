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
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 10,
                adFailureRate = 1.0,
                controlAttempts = 10,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
            environment = EnvironmentStats(false, false, null, mcc = 460),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.Unknown, result)
    }

    @Test
    fun `all probes reachable yields NotBlocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 0,
                adFailureRate = 0.0,
                controlAttempts = 10,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `ad failures with control success yields Blocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 10,
                adFailureRate = 1.0,
                controlAttempts = 10,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
            recentSignals = listOf(sampleProbe()),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Blocked, got $result", result is BlockState.Blocked)
    }

    @Test
    fun `ad and control both fail yields NotBlocked (network failure)`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 10,
                adFailureRate = 1.0,
                controlAttempts = 10,
                controlFailures = 10,
                controlFailureRate = 1.0,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `moderate ad failure with VPN bonus pushes to Suspected`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // ad delta 0.4 × 80 = 32, + vpn 10 = 42 → Suspected (threshold 40)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 4,
                adFailureRate = 0.4,
                controlAttempts = 10,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
            environment = EnvironmentStats(
                vpnActive = true,
                privateDnsActive = false,
                privateDnsServer = null,
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Suspected, got $result", result is BlockState.Suspected)
    }

    @Test
    fun `confidence low for small sample size`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 2,
                adFailures = 2,
                adFailureRate = 1.0,
                controlAttempts = 1,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
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
            probe = ProbeStats(
                adAttempts = 30,
                adFailures = 30,
                adFailureRate = 1.0,
                controlAttempts = 30,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
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

    @Test
    fun `private DNS pointing at known ad-blocker pushes to Blocked alone`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // No probe data; just the env signal matching an ad-blocker DNS.
        // 15 (privateDns) + 60 (known) = 75 → Blocked
        val snap = AggregateSnapshot.EMPTY.copy(
            environment = EnvironmentStats(
                vpnActive = false,
                privateDnsActive = true,
                privateDnsServer = "abc123.dns.nextdns.io",
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Blocked, got $result", result is BlockState.Blocked)
    }

    @Test
    fun `unknown private DNS server stays at modest score`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // Private DNS active but server not in registry → just +15, well below thresholds.
        val snap = AggregateSnapshot.EMPTY.copy(
            environment = EnvironmentStats(
                vpnActive = false,
                privateDnsActive = true,
                privateDnsServer = "dns.example.com",
                mcc = null,
            ),
            // Need some probe data for evaluator to not return Unknown.
            probe = ProbeStats(
                adAttempts = 3,
                adFailures = 0,
                adFailureRate = 0.0,
                controlAttempts = 3,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `installed ad-blocker package alone yields Suspected`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // 50 points (installed bonus) → between Suspected (40) and Blocked (70).
        val snap = AggregateSnapshot.EMPTY.copy(
            installedAdBlockerPackages = setOf("com.adguard.android"),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Suspected, got $result", result is BlockState.Suspected)
    }

    @Test
    fun `installed ad-blocker with VPN active stretches into Blocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // 50 (installed) + 10 (vpn) + 15 (privateDns) = 75 → Blocked
        val snap = AggregateSnapshot.EMPTY.copy(
            installedAdBlockerPackages = setOf("com.adguard.android"),
            environment = EnvironmentStats(
                vpnActive = true,
                privateDnsActive = true,
                privateDnsServer = "dns.example.com",
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Blocked, got $result", result is BlockState.Blocked)
    }

    @Test
    fun `extra DNS suffix triggers known-blocker bonus`() {
        val evaluator = BlockStateEvaluator(
            policy = defaultPolicy,
            extraAdBlockerDnsSuffixes = setOf("dns.consumer-custom.example"),
        )
        // 15 (privateDns) + 60 (known via extras) = 75 → Blocked
        val snap = AggregateSnapshot.EMPTY.copy(
            environment = EnvironmentStats(
                vpnActive = false,
                privateDnsActive = true,
                privateDnsServer = "dns.consumer-custom.example",
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertTrue("expected Blocked, got $result", result is BlockState.Blocked)
    }

    @Test
    fun `custom weights override defaults`() {
        val policyWithLightWeights = com.yongjincompany.anecdote.config.PolicyConfig.Builder().apply {
            weights = com.yongjincompany.anecdote.config.SignalWeights.DEFAULT.copy(
                knownAdBlockerDnsBonus = 5.0,
                privateDnsBonus = 5.0,
            )
        }.build()
        val evaluator = BlockStateEvaluator(policy = policyWithLightWeights)
        // With light weights, 5 + 5 = 10 → below Suspected threshold 40.
        val snap = AggregateSnapshot.EMPTY.copy(
            environment = EnvironmentStats(
                vpnActive = false,
                privateDnsActive = true,
                privateDnsServer = "dns.adguard-dns.com",
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertEquals(BlockState.NotBlocked, result)
    }

    @Test
    fun `strong ambient signal yields HIGH confidence without probe samples`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            environment = EnvironmentStats(
                vpnActive = false,
                privateDnsActive = true,
                privateDnsServer = "dns.adguard-dns.com",
                mcc = null,
            ),
        )
        val result = evaluator.evaluate(snap)
        val confidence = (result as BlockState.Blocked).confidence
        assertEquals(Confidence.HIGH, confidence)
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
