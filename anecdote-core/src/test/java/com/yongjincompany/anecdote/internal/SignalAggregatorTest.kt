package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalAggregatorTest {

    private class FakeSource(override val networkId: String) : AdNetworkSignalSource {
        private val _signals = MutableSharedFlow<AdNetworkSignal>(extraBufferCapacity = 64)
        override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()
        override fun start() {}
        override fun stop() {}

        suspend fun emit(signal: AdNetworkSignal) {
            _signals.emit(signal)
        }
    }

    private fun makeAggregator(
        scope: CoroutineScope,
        windowMs: Long = 120_000L,
        now: () -> Long = { 10_000L },
    ): SignalAggregator = SignalAggregator(
        scope = scope,
        windowDurationMs = windowMs,
        clock = Clock { now() },
    )

    @Test
    fun `probe signals populate probe stats`() = runTest {
        val source = FakeSource("probe")
        val aggregator = makeAggregator(backgroundScope)

        aggregator.register(source)
        testScheduler.runCurrent()

        source.emit(probeResult(timestamp = 9_000L, domain = "ad.example", isControl = false, reachable = false))
        source.emit(probeResult(timestamp = 9_000L, domain = "ad2.example", isControl = false, reachable = true))
        source.emit(probeResult(timestamp = 9_000L, domain = "ctrl.example", isControl = true, reachable = true))
        testScheduler.runCurrent()

        val snap = aggregator.snapshot.value
        assertEquals(2, snap.probe.adAttempts)
        assertEquals(1, snap.probe.adFailures)
        assertEquals(0.5, snap.probe.adFailureRate, 0.0001)
        assertEquals(1, snap.probe.controlAttempts)
        assertEquals(0.0, snap.probe.controlFailureRate, 0.0001)
    }

    @Test
    fun `load signals populate per-network stats`() = runTest {
        val source = FakeSource("admob")
        val aggregator = makeAggregator(backgroundScope)

        aggregator.register(source)
        testScheduler.runCurrent()

        repeat(3) { source.emit(loadFailed("admob", timestamp = 9_500L)) }
        source.emit(loadSucceeded("admob", timestamp = 9_500L))
        source.emit(loadSucceeded("applovin", timestamp = 9_500L))
        testScheduler.runCurrent()

        val snap = aggregator.snapshot.value
        val admob = snap.networks.getValue("admob")
        assertEquals(4, admob.loadAttempts)
        assertEquals(3, admob.loadFailures)
        assertEquals(0.75, admob.loadFailureRate, 0.0001)

        val applovin = snap.networks.getValue("applovin")
        assertEquals(1, applovin.loadAttempts)
        assertEquals(0, applovin.loadFailures)
    }

    @Test
    fun `environment signal updates environment but not events list`() = runTest {
        val source = FakeSource("env")
        val aggregator = makeAggregator(backgroundScope)

        aggregator.register(source)
        testScheduler.runCurrent()

        source.emit(
            AdNetworkSignal.NetworkEnvironment(
                networkId = AdNetworkSignal.NetworkEnvironment.NETWORK_ID,
                timestamp = 9_000L,
                vpnActive = true,
                privateDnsActive = false,
                privateDnsServer = null,
                mcc = 450,
            ),
        )
        testScheduler.runCurrent()

        val snap = aggregator.snapshot.value
        assertNotNull(snap.environment)
        assertEquals(true, snap.environment?.vpnActive)
        assertEquals(450, snap.environment?.mcc)
        assertEquals(0, snap.probe.adAttempts)
    }

    @Test
    fun `sliding window prunes old events`() = runTest {
        val source = FakeSource("probe")
        var now = 10_000L
        val aggregator = makeAggregator(backgroundScope, windowMs = 1_000L) { now }

        aggregator.register(source)
        testScheduler.runCurrent()

        source.emit(probeResult(timestamp = 8_000L, domain = "old.example", isControl = false, reachable = false))
        testScheduler.runCurrent()
        assertEquals(0, aggregator.snapshot.value.probe.adAttempts) // pruned immediately

        source.emit(probeResult(timestamp = 9_500L, domain = "fresh.example", isControl = false, reachable = true))
        testScheduler.runCurrent()
        assertEquals(1, aggregator.snapshot.value.probe.adAttempts)

        now = 12_000L
        source.emit(probeResult(timestamp = 11_500L, domain = "even-fresher.example", isControl = false, reachable = true))
        testScheduler.runCurrent()
        // 11_500 within [11_000, 12_000]; 9_500 is now outside
        val snap = aggregator.snapshot.value
        assertEquals(1, snap.probe.adAttempts)
    }

    @Test
    fun `unregister stops receiving signals`() = runTest {
        val source = FakeSource("admob")
        val aggregator = makeAggregator(backgroundScope)

        aggregator.register(source)
        testScheduler.runCurrent()
        source.emit(loadFailed("admob", timestamp = 9_500L))
        testScheduler.runCurrent()
        assertEquals(1, aggregator.snapshot.value.networks["admob"]?.loadAttempts)

        aggregator.unregister(source)
        testScheduler.runCurrent()

        source.emit(loadFailed("admob", timestamp = 9_600L))
        testScheduler.runCurrent()
        assertEquals(1, aggregator.snapshot.value.networks["admob"]?.loadAttempts)
    }

    private fun probeResult(
        timestamp: Long,
        domain: String,
        isControl: Boolean,
        reachable: Boolean,
        latencyMs: Long? = 10L,
    ) = AdNetworkSignal.ProbeResult(
        networkId = AdNetworkSignal.ProbeResult.NETWORK_ID,
        timestamp = timestamp,
        domain = domain,
        isControl = isControl,
        reachable = reachable,
        latencyMs = latencyMs,
    )

    private fun loadFailed(networkId: String, timestamp: Long) = AdNetworkSignal.LoadFailed(
        networkId = networkId,
        timestamp = timestamp,
        errorType = com.yongjincompany.anecdote.signal.ErrorType.NETWORK_ERROR,
        rawErrorCode = null,
        rawErrorMessage = null,
    )

    private fun loadSucceeded(networkId: String, timestamp: Long) = AdNetworkSignal.LoadSucceeded(
        networkId = networkId,
        timestamp = timestamp,
    )
}
