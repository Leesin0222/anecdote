package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.config.ProbeConfig
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AdDomainProber(
    private val config: ProbeConfig,
    private val checker: HttpReachabilityChecker,
    private val clock: Clock = SystemClock,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AdNetworkSignalSource {

    override val networkId: String = AdNetworkSignal.ProbeResult.NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var cycleJob: Job? = null

    override fun start() {
        if (cycleJob?.isActive == true) return
        cycleJob = scope.launch {
            while (isActive) {
                runProbeCycle()
                delay(config.intervalMs)
            }
        }
    }

    override fun stop() {
        cycleJob?.cancel()
        cycleJob = null
    }

    internal suspend fun runProbeCycle() {
        val targets = buildList {
            config.adDomains.forEach { add(it to false) }
            config.controlDomains.forEach { add(it to true) }
        }

        val results = coroutineScope {
            targets.map { (domain, isControl) ->
                async { probeDomain(domain, isControl) }
            }.awaitAll()
        }

        results.forEach { _signals.emit(it) }
    }

    private suspend fun probeDomain(
        domain: String,
        isControl: Boolean,
    ): AdNetworkSignal.ProbeResult {
        val result = checker.check("https://$domain/")
        return AdNetworkSignal.ProbeResult(
            networkId = AdNetworkSignal.ProbeResult.NETWORK_ID,
            timestamp = clock.now(),
            domain = domain,
            isControl = isControl,
            reachable = result.reachable,
            latencyMs = result.latencyMs,
        )
    }
}
