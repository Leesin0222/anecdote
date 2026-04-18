package com.yongjincompany.anecdote.internal

import app.cash.turbine.test
import com.yongjincompany.anecdote.config.ProbeConfig
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdDomainProberTest {

    private val fixedClock = Clock { FIXED_TIME }

    private fun probeConfig(
        adDomains: List<String> = listOf("ad1.example", "ad2.example"),
        controlDomains: List<String> = listOf("ctrl.example"),
        intervalMs: Long = 60_000L,
    ): ProbeConfig = ProbeConfig.Builder().apply {
        this.adDomains = adDomains
        this.controlDomains = controlDomains
        this.intervalMs = intervalMs
    }.build()

    @Test
    fun `runProbeCycle emits one signal per ad and control domain`() = runTest {
        val scripted = mapOf(
            "https://ad1.example/" to HttpReachabilityResult(true, 12),
            "https://ad2.example/" to HttpReachabilityResult(false, null),
            "https://ctrl.example/" to HttpReachabilityResult(true, 20),
        )
        val checker = HttpReachabilityChecker { url -> scripted.getValue(url) }
        val prober = AdDomainProber(
            config = probeConfig(),
            checker = checker,
            clock = fixedClock,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        prober.signals.test {
            prober.runProbeCycle()

            val emitted = List(3) { awaitItem() as AdNetworkSignal.ProbeResult }
            val byDomain = emitted.associateBy { it.domain }
            assertEquals(3, byDomain.size)

            val ad1 = byDomain.getValue("ad1.example")
            assertFalse(ad1.isControl)
            assertTrue(ad1.reachable)
            assertEquals(12L, ad1.latencyMs)
            assertEquals(FIXED_TIME, ad1.timestamp)
            assertEquals(AdNetworkSignal.ProbeResult.NETWORK_ID, ad1.networkId)

            val ad2 = byDomain.getValue("ad2.example")
            assertFalse(ad2.isControl)
            assertFalse(ad2.reachable)
            assertNull(ad2.latencyMs)

            val ctrl = byDomain.getValue("ctrl.example")
            assertTrue(ctrl.isControl)
            assertTrue(ctrl.reachable)
        }
    }

    @Test
    fun `start launches periodic cycles and stop cancels them`() = runTest {
        var callCount = 0
        val checker = HttpReachabilityChecker {
            callCount++
            HttpReachabilityResult(true, 5)
        }
        val prober = AdDomainProber(
            config = probeConfig(intervalMs = 1_000L),
            checker = checker,
            clock = fixedClock,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        prober.start()
        testScheduler.runCurrent()
        assertEquals(3, callCount)

        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()
        assertEquals(6, callCount)

        prober.stop()
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(6, callCount)
    }

    private companion object {
        const val FIXED_TIME = 1_000L
    }
}
