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
}
