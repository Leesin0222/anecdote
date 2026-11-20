package com.yongjincompany.anecdote

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdBlockDetectorPolicyTest {

    private fun detector(): AdBlockDetector {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        return AdBlockDetector.Builder(context)
            .policy { thresholdBlocked = 80 }
            .build()
    }

    @Test
    fun `updatePolicy changes only the fields set in the block`() {
        val d = detector()
        d.updatePolicy { thresholdSuspected = 25 }

        assertEquals(25, d.policy.thresholdSuspected)
        assertEquals(80, d.policy.thresholdBlocked) // kept from builder
    }

    @Test
    fun `invalid update throws and keeps previous policy`() {
        val d = detector()
        assertThrows(IllegalArgumentException::class.java) {
            d.updatePolicy { thresholdSuspected = 90 } // > thresholdBlocked
        }
        assertEquals(40, d.policy.thresholdSuspected)
        assertEquals(80, d.policy.thresholdBlocked)
    }

    @Test
    fun `updatePolicy before start is safe`() {
        val d = detector()
        d.updatePolicy { weights = weights.copy(vpnBonus = 0.0) }
        assertEquals(0.0, d.policy.weights.vpnBonus, 0.0)
        assertEquals(BlockState.Unknown, d.state.value)
    }
}
