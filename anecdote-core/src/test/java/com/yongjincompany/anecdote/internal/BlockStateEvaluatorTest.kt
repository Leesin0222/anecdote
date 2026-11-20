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
    fun `small probe sample is scaled down and capped below Blocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // delta 1.0 × 80 × (2/4 ad) × (2/2 ctrl) = 40 → Suspected, not Blocked.
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 2,
                adFailures = 2,
                adFailureRate = 1.0,
                controlAttempts = 2,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
        )
        val result = evaluator.evaluate(snap)
        assertNotNull(result)
        assertTrue("expected Suspected, got $result", result is BlockState.Suspected)
        assertEquals(Confidence.LOW, (result as BlockState.Suspected).confidence)
    }

    @Test
    fun `single failed ad probe cannot reach Blocked`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 1,
                adFailures = 1,
                adFailureRate = 1.0,
                controlAttempts = 1,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
        )
        // 80 × 0.25 × 0.5 = 10 → NotBlocked
        assertEquals(BlockState.NotBlocked, evaluator.evaluate(snap))
    }

    @Test
    fun `probe without control samples contributes nothing`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 10,
                adFailures = 10,
                adFailureRate = 1.0,
                controlAttempts = 0,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
        )
        assertEquals(0, evaluator.computeScore(snap))
    }

    @Test
    fun `ambient signals are discounted when ads are demonstrably reachable`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // Raw ambient: 50 (installed) + 15 (privateDns) + 60 (known) + 10 (vpn) = 135.
        // Ads reachable → × 0.2 = 27 → NotBlocked.
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = reachableProbe(),
            installedAdBlockerPackages = setOf("com.adguard.android"),
            environment = EnvironmentStats(
                vpnActive = true,
                privateDnsActive = true,
                privateDnsServer = "abc123.dns.nextdns.io",
                mcc = null,
            ),
        )
        assertEquals(BlockState.NotBlocked, evaluator.evaluate(snap))
    }

    @Test
    fun `ambient signals are not discounted when probe sample is insufficient`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // Only 2 ad samples (< 4): discount does not apply. 50 + 15 = 65 → Suspected.
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 2,
                adFailures = 0,
                adFailureRate = 0.0,
                controlAttempts = 2,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
            installedAdBlockerPackages = setOf("com.adguard.android"),
            environment = EnvironmentStats(false, true, "dns.google", null),
        )
        assertTrue(evaluator.evaluate(snap) is BlockState.Suspected)
    }

    @Test
    fun `ambient signals are not discounted when ads fail and control is down too`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // Offline: ads not "reachable", so the known DNS keeps full weight (75 → Blocked).
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 8,
                adFailures = 8,
                adFailureRate = 1.0,
                controlAttempts = 2,
                controlFailures = 2,
                controlFailureRate = 1.0,
            ),
            environment = EnvironmentStats(false, true, "dns.adguard.com", null),
        )
        assertTrue(evaluator.evaluate(snap) is BlockState.Blocked)
    }

    @Test
    fun `strong ambient signal agreeing with direct evidence lifts confidence`() {
        val evaluator = BlockStateEvaluator(defaultPolicy)
        // 10 samples → MEDIUM base; known DNS lifts it to HIGH.
        val snap = AggregateSnapshot.EMPTY.copy(
            probe = ProbeStats(
                adAttempts = 8,
                adFailures = 8,
                adFailureRate = 1.0,
                controlAttempts = 2,
                controlFailures = 0,
                controlFailureRate = 0.0,
            ),
            environment = EnvironmentStats(false, true, "dns.adguard.com", null),
        )
        val result = evaluator.evaluate(snap) as BlockState.Blocked
        assertEquals(Confidence.HIGH, result.confidence)
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
    fun `ambient-only verdict is capped at MEDIUM confidence`() {
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
        assertEquals(Confidence.MEDIUM, confidence)
    }

    private fun reachableProbe() = ProbeStats(
        adAttempts = 8,
        adFailures = 0,
        adFailureRate = 0.0,
        controlAttempts = 2,
        controlFailures = 0,
        controlFailureRate = 0.0,
    )

    private fun sampleProbe() = AdNetworkSignal.ProbeResult(
        networkId = "probe",
        timestamp = 1_000L,
        domain = "ad.example",
        isControl = false,
        reachable = false,
        latencyMs = null,
    )
}
