package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SignalAggregator(
    private val scope: CoroutineScope,
    private val windowDurationMs: Long = DEFAULT_WINDOW_MS,
    private val clock: Clock = SystemClock,
) {
    private val mutex = Mutex()
    private val subscriptionJobs = linkedMapOf<AdNetworkSignalSource, Job>()

    // Guarded by mutex
    private val events: ArrayDeque<AdNetworkSignal> = ArrayDeque()
    private var latestEnvironment: AdNetworkSignal.NetworkEnvironment? = null

    private val _snapshot = MutableStateFlow(AggregateSnapshot.EMPTY)
    val snapshot: StateFlow<AggregateSnapshot> = _snapshot.asStateFlow()

    fun register(source: AdNetworkSignalSource) {
        if (subscriptionJobs.containsKey(source)) return
        subscriptionJobs[source] = scope.launch {
            source.signals.collect { signal ->
                handleSignal(signal)
            }
        }
    }

    fun unregister(source: AdNetworkSignalSource) {
        subscriptionJobs.remove(source)?.cancel()
    }

    fun stop() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
    }

    /**
     * Manually inject a signal. Used by [com.yongjincompany.anecdote.signal.AdNetworkSignalSource]
     * implementations that cannot naturally bridge to a [kotlinx.coroutines.flow.SharedFlow].
     */
    suspend fun submit(signal: AdNetworkSignal) {
        handleSignal(signal)
    }

    private suspend fun handleSignal(signal: AdNetworkSignal) {
        mutex.withLock {
            when (signal) {
                is AdNetworkSignal.NetworkEnvironment -> latestEnvironment = signal
                else -> events.addLast(signal)
            }
            purgeOldEvents(clock.now())
            _snapshot.value = computeSnapshot()
        }
    }

    private fun purgeOldEvents(now: Long) {
        val threshold = now - windowDurationMs
        while (events.isNotEmpty() && events.first().timestamp < threshold) {
            events.removeFirst()
        }
    }

    private fun computeSnapshot(): AggregateSnapshot {
        val probeEvents = events.filterIsInstance<AdNetworkSignal.ProbeResult>()
        val adProbes = probeEvents.filter { !it.isControl }
        val ctrlProbes = probeEvents.filter { it.isControl }

        val probe = ProbeStats(
            adAttempts = adProbes.size,
            adFailures = adProbes.count { !it.reachable },
            adFailureRate = adProbes.failureRate(),
            controlAttempts = ctrlProbes.size,
            controlFailures = ctrlProbes.count { !it.reachable },
            controlFailureRate = ctrlProbes.failureRate(),
        )

        val loadEvents = events.mapNotNull { ev ->
            when (ev) {
                is AdNetworkSignal.LoadFailed -> ev.networkId to true
                is AdNetworkSignal.LoadSucceeded -> ev.networkId to false
                else -> null
            }
        }
        val networks = loadEvents
            .groupBy({ it.first }, { it.second })
            .mapValues { (networkId, isFailures) ->
                val attempts = isFailures.size
                val failures = isFailures.count { it }
                NetworkStats(
                    networkId = networkId,
                    loadAttempts = attempts,
                    loadFailures = failures,
                    loadFailureRate = if (attempts == 0) 0.0 else failures.toDouble() / attempts,
                )
            }

        val env = latestEnvironment?.let {
            EnvironmentStats(
                vpnActive = it.vpnActive,
                privateDnsActive = it.privateDnsActive,
                privateDnsServer = it.privateDnsServer,
                mcc = it.mcc,
            )
        }

        return AggregateSnapshot(
            networks = networks,
            probe = probe,
            environment = env,
            recentSignals = events.toList() + listOfNotNull(latestEnvironment),
        )
    }

    private fun List<AdNetworkSignal.ProbeResult>.failureRate(): Double {
        if (isEmpty()) return 0.0
        val failures = count { !it.reachable }
        return failures.toDouble() / size
    }

    companion object {
        const val DEFAULT_WINDOW_MS: Long = 120_000L // 2 minutes
    }
}
