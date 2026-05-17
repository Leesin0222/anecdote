package com.yongjincompany.anecdote.internal

import app.cash.turbine.test
import com.yongjincompany.anecdote.config.ProbeConfig
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        intervalSequenceMs: List<Long> = listOf(60_000L),
    ): ProbeConfig = ProbeConfig.Builder().apply {
        this.adDomains = adDomains
        this.controlDomains = controlDomains
        this.intervalSequenceMs = intervalSequenceMs
    }.build()

    private fun resolvingDns(): DnsReachabilityChecker = DnsReachabilityChecker { _ ->
        DnsResolution(resolved = true, allSinkholed = false, lookupSucceeded = true)
    }

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
            scope = backgroundScope,
            dnsChecker = resolvingDns(),
            clock = fixedClock,
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
    fun `dns block short-circuits HTTP probe`() = runTest {
        var httpCalls = 0
        val checker = HttpReachabilityChecker {
            httpCalls++
            HttpReachabilityResult(true, 10)
        }
        // DNS reports NXDOMAIN/sinkhole for every host.
        val dns = DnsReachabilityChecker { _ ->
            DnsResolution(resolved = false, allSinkholed = true, lookupSucceeded = true)
        }
        val prober = AdDomainProber(
            config = probeConfig(),
            checker = checker,
            scope = backgroundScope,
            dnsChecker = dns,
            clock = fixedClock,
        )

        prober.signals.test {
            prober.runProbeCycle()
            val emitted = List(3) { awaitItem() as AdNetworkSignal.ProbeResult }
            assertTrue("all probes must be marked unreachable", emitted.all { !it.reachable })
            assertTrue("latency must be null when DNS short-circuits", emitted.all { it.latencyMs == null })
        }
        assertEquals("HTTP must never be invoked when DNS already says no", 0, httpCalls)
    }

    @Test
    fun `start launches periodic cycles and stop cancels them`() = runTest {
        var callCount = 0
        val checker = HttpReachabilityChecker {
            callCount++
            HttpReachabilityResult(true, 5)
        }
        val prober = AdDomainProber(
            config = probeConfig(intervalSequenceMs = listOf(1_000L)),
            checker = checker,
            scope = backgroundScope,
            dnsChecker = resolvingDns(),
            clock = fixedClock,
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

    @Test
    fun `adaptive interval walks through sequence and pins at last`() = runTest {
        var callCount = 0
        val checker = HttpReachabilityChecker {
            callCount++
            HttpReachabilityResult(true, 5)
        }
        val prober = AdDomainProber(
            config = probeConfig(intervalSequenceMs = listOf(100L, 200L, 400L)),
            checker = checker,
            scope = backgroundScope,
            dnsChecker = resolvingDns(),
            clock = fixedClock,
        )

        prober.start()
        testScheduler.runCurrent()
        // Cycle 0: 3 calls
        assertEquals(3, callCount)

        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()
        assertEquals(6, callCount) // cycle 1

        testScheduler.advanceTimeBy(200L)
        testScheduler.runCurrent()
        assertEquals(9, callCount) // cycle 2

        testScheduler.advanceTimeBy(400L)
        testScheduler.runCurrent()
        assertEquals(12, callCount) // cycle 3 (pinned at 400ms)

        testScheduler.advanceTimeBy(400L)
        testScheduler.runCurrent()
        assertEquals(15, callCount) // cycle 4 (still pinned)
    }

    @Test
    fun `resetSchedule jumps back to head and wakes the loop early`() = runTest {
        var callCount = 0
        val checker = HttpReachabilityChecker {
            callCount++
            HttpReachabilityResult(true, 5)
        }
        val prober = AdDomainProber(
            config = probeConfig(intervalSequenceMs = listOf(100L, 10_000L)),
            checker = checker,
            scope = backgroundScope,
            dnsChecker = resolvingDns(),
            clock = fixedClock,
        )

        prober.start()
        testScheduler.runCurrent()
        assertEquals(3, callCount) // cycle 0

        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()
        assertEquals(6, callCount) // cycle 1 — next delay would be 10s

        // Without reset, no new cycle should happen for 10 seconds. Reset should fire one right away.
        prober.resetSchedule()
        testScheduler.runCurrent()
        assertEquals(9, callCount) // cycle 2 triggered by wakeup
    }

    @Test
    fun `checker throwing non-IOException does not kill probe loop`() = runTest {
        var failedCycles = 0
        var successCycles = 0
        val checker = HttpReachabilityChecker {
            if (failedCycles == 0 && it == "https://ad1.example/") {
                failedCycles++
                throw IllegalArgumentException("bad url")
            }
            successCycles++
            HttpReachabilityResult(true, 10)
        }
        val prober = AdDomainProber(
            config = probeConfig(intervalSequenceMs = listOf(1_000L)),
            checker = checker,
            scope = backgroundScope,
            dnsChecker = resolvingDns(),
            clock = fixedClock,
        )

        prober.start()
        testScheduler.runCurrent()
        val firstBurst = successCycles

        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()
        assertTrue("loop must keep running after transient throw; successCycles=$successCycles", successCycles > firstBurst)
    }

    private companion object {
        const val FIXED_TIME = 1_000L
    }
}
