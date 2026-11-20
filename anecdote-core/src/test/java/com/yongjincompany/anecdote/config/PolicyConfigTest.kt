package com.yongjincompany.anecdote.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolicyConfigTest {

    @Test
    fun `defaults build successfully`() {
        val cfg = PolicyConfig.Builder().build()
        assertEquals(40, cfg.thresholdSuspected)
        assertEquals(70, cfg.thresholdBlocked)
        assertEquals(3, cfg.maxEvaluationsPerSession)
    }

    @Test
    fun `thresholdSuspected out of range rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { thresholdSuspected = -1 }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { thresholdSuspected = 101 }.build()
        }
    }

    @Test
    fun `thresholdBlocked out of range rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { thresholdBlocked = -1 }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { thresholdBlocked = 200 }.build()
        }
    }

    @Test
    fun `inverted thresholds rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply {
                thresholdSuspected = 90
                thresholdBlocked = 30
            }.build()
        }
    }

    @Test
    fun `zero or negative maxEvaluationsPerSession rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { maxEvaluationsPerSession = 0 }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { maxEvaluationsPerSession = -5 }.build()
        }
    }

    @Test
    fun `equal thresholds accepted`() {
        val cfg = PolicyConfig.Builder().apply {
            thresholdSuspected = 50
            thresholdBlocked = 50
        }.build()
        assertEquals(50, cfg.thresholdSuspected)
        assertEquals(50, cfg.thresholdBlocked)
    }

    @Test
    fun `non-positive probe sample minimums rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { minAdProbeSamples = 0 }.build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolicyConfig.Builder().apply { minControlProbeSamples = 0 }.build()
        }
    }

    @Test
    fun `toBuilder round-trips every field`() {
        val original = PolicyConfig.Builder().apply {
            thresholdSuspected = 30
            thresholdBlocked = 60
            maxEvaluationsPerSession = 7
            disabledRegionMccs = setOf(1, 2)
            weights = SignalWeights.DEFAULT.copy(vpnBonus = 1.0)
            minAdProbeSamples = 6
            minControlProbeSamples = 3
        }.build()
        val copy = original.toBuilder().build()
        assertEquals(original.thresholdSuspected, copy.thresholdSuspected)
        assertEquals(original.thresholdBlocked, copy.thresholdBlocked)
        assertEquals(original.maxEvaluationsPerSession, copy.maxEvaluationsPerSession)
        assertEquals(original.disabledRegionMccs, copy.disabledRegionMccs)
        assertEquals(original.weights, copy.weights)
        assertEquals(original.minAdProbeSamples, copy.minAdProbeSamples)
        assertEquals(original.minControlProbeSamples, copy.minControlProbeSamples)
    }

    @Test
    fun `ambient discount outside 0 to 1 rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SignalWeights(ambientDiscountWhenAdsReachable = 1.5)
        }
    }
}
