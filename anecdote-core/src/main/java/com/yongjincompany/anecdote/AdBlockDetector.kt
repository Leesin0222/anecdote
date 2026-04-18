package com.yongjincompany.anecdote

import android.content.Context
import com.yongjincompany.anecdote.config.DetectorConfig
import com.yongjincompany.anecdote.config.PolicyConfig
import com.yongjincompany.anecdote.config.ProbeConfig
import com.yongjincompany.anecdote.internal.AdDomainProber
import com.yongjincompany.anecdote.internal.AggregateSnapshot
import com.yongjincompany.anecdote.internal.AndroidNetworkChangeRegistrar
import com.yongjincompany.anecdote.internal.AndroidNetworkEnvironmentReader
import com.yongjincompany.anecdote.internal.BlockStateEvaluator
import com.yongjincompany.anecdote.internal.DetectorGate
import com.yongjincompany.anecdote.internal.NetworkEnvironmentSource
import com.yongjincompany.anecdote.internal.OkHttpReachabilityChecker
import com.yongjincompany.anecdote.internal.SignalAggregator
import com.yongjincompany.anecdote.report.BlockEventReporter
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public class AdBlockDetector private constructor(
    private val config: DetectorConfig,
) {

    private val evaluator = BlockStateEvaluator(config.policyConfig)
    private val gate = DetectorGate(
        gracePeriodMs = config.probeConfig.gracePeriodMs,
        maxEvaluationsPerSession = config.policyConfig.maxEvaluationsPerSession,
    )

    // Single shared OkHttpClient reused across start/stop cycles to avoid
    // accumulating connection pools and executor threads.
    private val okHttpClient = OkHttpReachabilityChecker.defaultClient(
        timeoutMs = config.probeConfig.timeoutMs,
    )

    private val _state = MutableStateFlow<BlockState>(BlockState.Unknown)
    public val state: StateFlow<BlockState> = _state.asStateFlow()

    private val lock = Any()

    // All fields below are guarded by [lock].
    private var scope: CoroutineScope? = null
    private var aggregator: SignalAggregator? = null
    private val builtInSources: MutableList<AdNetworkSignalSource> = mutableListOf()
    private val dynamicSources: MutableList<AdNetworkSignalSource> = mutableListOf()
    private val reporterForwardJobs: MutableMap<AdNetworkSignalSource, Job> = mutableMapOf()
    private var evaluationJob: Job? = null

    public fun start() {
        synchronized(lock) {
            if (scope != null) return

            val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val newAggregator = SignalAggregator(scope = newScope)
            scope = newScope
            aggregator = newAggregator

            gate.onStart(System.currentTimeMillis())
            _state.value = BlockState.Unknown

            val probe = AdDomainProber(
                config = config.probeConfig,
                checker = OkHttpReachabilityChecker(client = okHttpClient),
                scope = newScope,
            )
            val env = NetworkEnvironmentSource(
                reader = AndroidNetworkEnvironmentReader(config.context),
                registrar = AndroidNetworkChangeRegistrar(config.context),
            )
            builtInSources.clear()
            builtInSources += probe
            builtInSources += env

            (builtInSources + config.signalSources + dynamicSources).forEach { source ->
                attachSourceLocked(source, newScope, newAggregator)
            }

            evaluationJob = newScope.launch {
                newAggregator.snapshot.collect { snap -> reevaluateInternal(snap) }
            }
        }
    }

    public fun stop() {
        synchronized(lock) {
            val currentScope = scope ?: return

            evaluationJob?.cancel()
            evaluationJob = null

            (builtInSources + config.signalSources + dynamicSources).forEach { it.stop() }
            builtInSources.clear()
            reporterForwardJobs.values.forEach { it.cancel() }
            reporterForwardJobs.clear()
            aggregator?.stop()
            aggregator = null
            currentScope.cancel()
            scope = null
        }
    }

    public fun reevaluate() {
        val snap: AggregateSnapshot = synchronized(lock) {
            aggregator?.snapshot?.value ?: return
        }
        reevaluateInternal(snap)
    }

    public fun registerSignalSource(source: AdNetworkSignalSource) {
        synchronized(lock) {
            if (dynamicSources.contains(source)) return
            dynamicSources += source
            val currentScope = scope
            val currentAggregator = aggregator
            if (currentScope != null && currentAggregator != null) {
                attachSourceLocked(source, currentScope, currentAggregator)
            }
        }
    }

    public fun unregisterSignalSource(source: AdNetworkSignalSource) {
        synchronized(lock) {
            if (!dynamicSources.remove(source)) return
            if (scope != null) {
                source.stop()
                detachSourceLocked(source)
            }
        }
    }

    private fun attachSourceLocked(
        source: AdNetworkSignalSource,
        currentScope: CoroutineScope,
        currentAggregator: SignalAggregator,
    ) {
        currentAggregator.register(source)
        if (config.reporters.isNotEmpty()) {
            reporterForwardJobs[source] = currentScope.launch {
                source.signals.collect { signal ->
                    config.reporters.forEach { it.onSignalReceived(signal) }
                }
            }
        }
        source.start()
    }

    private fun detachSourceLocked(source: AdNetworkSignalSource) {
        aggregator?.unregister(source)
        reporterForwardJobs.remove(source)?.cancel()
    }

    @Synchronized
    private fun reevaluateInternal(snapshot: AggregateSnapshot) {
        val now = System.currentTimeMillis()
        if (!gate.shouldEvaluate(now, snapshot.environment)) return

        val newState = evaluator.evaluate(snapshot)
        val oldState = _state.value
        if (newState == oldState) return

        _state.value = newState
        gate.onStateTransitioned(newState)

        config.reporters.forEach { it.onStateChanged(newState) }
    }

    public class Builder(private val context: Context) {
        private var probeConfigBuilder: ProbeConfig.Builder = ProbeConfig.Builder()
        private var policyConfigBuilder: PolicyConfig.Builder = PolicyConfig.Builder()
        private val reporters: MutableList<BlockEventReporter> = mutableListOf()
        private val signalSources: MutableList<AdNetworkSignalSource> = mutableListOf()

        public fun probe(block: ProbeConfig.Builder.() -> Unit): Builder = apply {
            probeConfigBuilder.apply(block)
        }

        public fun policy(block: PolicyConfig.Builder.() -> Unit): Builder = apply {
            policyConfigBuilder.apply(block)
        }

        public fun addReporter(reporter: BlockEventReporter): Builder = apply {
            reporters += reporter
        }

        public fun addSignalSource(source: AdNetworkSignalSource): Builder = apply {
            signalSources += source
        }

        public fun build(): AdBlockDetector {
            val cfg = DetectorConfig(
                context = context.applicationContext,
                probeConfig = probeConfigBuilder.build(),
                policyConfig = policyConfigBuilder.build(),
                reporters = reporters.toList(),
                signalSources = signalSources.toList(),
            )
            return AdBlockDetector(cfg)
        }
    }
}
