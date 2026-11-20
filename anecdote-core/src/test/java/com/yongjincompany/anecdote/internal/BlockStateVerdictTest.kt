package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStateVerdictTest {

    private val signal = AdNetworkSignal.LoadSucceeded(networkId = "admob", timestamp = 1L)

    @Test
    fun `same kind and confidence with different signals is the same verdict`() {
        val a = BlockState.Blocked(Confidence.MEDIUM, emptyList())
        val b = BlockState.Blocked(Confidence.MEDIUM, listOf(signal))
        assertTrue(a.hasSameVerdictAs(b))
    }

    @Test
    fun `confidence change is a different verdict`() {
        val a = BlockState.Suspected(Confidence.LOW, emptyList())
        val b = BlockState.Suspected(Confidence.MEDIUM, emptyList())
        assertFalse(a.hasSameVerdictAs(b))
    }

    @Test
    fun `kind change is a different verdict`() {
        assertFalse(BlockState.NotBlocked.hasSameVerdictAs(BlockState.Unknown))
        assertFalse(
            BlockState.Suspected(Confidence.HIGH, emptyList())
                .hasSameVerdictAs(BlockState.Blocked(Confidence.HIGH, emptyList())),
        )
        assertTrue(BlockState.Unknown.hasSameVerdictAs(BlockState.Unknown))
    }
}
