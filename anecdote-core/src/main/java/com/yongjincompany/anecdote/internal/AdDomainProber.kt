package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.config.ProbeConfig
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class AdDomainProber(
    private val config: ProbeConfig,
    private val checker: HttpReachabilityChecker,
    private val scope: CoroutineScope,
    private val dnsChecker: DnsReachabilityChecker = InetAddressDnsChecker(timeoutMs = config.timeoutMs),
    private val clock: Clock = SystemClock,
) : AdNetworkSignalSource {

    override val networkId: String = AdNetworkSignal.ProbeResult.NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    private var cycleJob: Job? = null
    private var cycleIndex: Int = 0

    // Conflated so multiple back-to-back reset signals collapse into one wakeup.
    private val wakeup = Channel<Unit>(Channel.CONFLATED)

    override fun start() {
        if (cycleJob?.isActive == true) return
        cycleIndex = 0
        cycleJob = scope.launch {
            while (isActive) {
                try {
                    runProbeCycle()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Swallow unexpected exceptions from a single cycle so the loop survives.
                }
                val delayMs = nextDelayMs()
                cycleIndex++
                waitForNextCycle(delayMs)
            }
        }
    }

    override fun stop() {
        cycleJob?.cancel()
        cycleJob = null
    }

    /**
     * Restart the backoff sequence at the head and trigger an immediate probe cycle.
     * Call when the network environment changes so we converge quickly.
     */
    fun resetSchedule() {
        cycleIndex = 0
        wakeup.trySend(Unit)
    }

    private fun nextDelayMs(): Long {
        val seq = config.intervalSequenceMs
        val idx = cycleIndex.coerceAtMost(seq.size - 1)
        return seq[idx]
    }

    private suspend fun waitForNextCycle(delayMs: Long) {
        // withTimeoutOrNull returns null on timeout (i.e., the full delay elapsed),
        // or Unit if a wakeup was received early.
        withTimeoutOrNull(delayMs) { wakeup.receive() }
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
        // Stage 1: DNS. Cheap, and catches the dominant ad-blocking vector.
        val dns = try {
            dnsChecker.resolve(domain)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            DnsResolution(resolved = false, allSinkholed = false, lookupSucceeded = false)
        }

        if (!dns.resolved) {
            // Either NXDOMAIN, all-sinkhole, or transport failure — no point doing HTTP.
            return AdNetworkSignal.ProbeResult(
                networkId = AdNetworkSignal.ProbeResult.NETWORK_ID,
                timestamp = clock.now(),
                domain = domain,
                isControl = isControl,
                reachable = false,
                latencyMs = null,
            )
        }

        // Stage 2: HTTP HEAD. Catches IP-level blocking that survives DNS resolution.
        val http = try {
            checker.check("https://$domain/")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            HttpReachabilityResult(reachable = false, latencyMs = null)
        }
        return AdNetworkSignal.ProbeResult(
            networkId = AdNetworkSignal.ProbeResult.NETWORK_ID,
            timestamp = clock.now(),
            domain = domain,
            isControl = isControl,
            reachable = http.reachable,
            latencyMs = http.latencyMs,
        )
    }
}
