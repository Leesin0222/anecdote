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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public class AdBlockDetector private constructor(
    private val config: DetectorConfig,
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val aggregator = SignalAggregator(scope = scope)
    private val evaluator = BlockStateEvaluator(config.policyConfig)
    private val gate = DetectorGate(
        gracePeriodMs = config.probeConfig.gracePeriodMs,
        maxEvaluationsPerSession = config.policyConfig.maxEvaluationsPerSession,
    )

    private val _state = MutableStateFlow<BlockState>(BlockState.Unknown)
    public val state: StateFlow<BlockState> = _state.asStateFlow()

    private val builtInSources: MutableList<AdNetworkSignalSource> = mutableListOf()
    private val reporterForwardJobs: MutableMap<AdNetworkSignalSource, Job> = mutableMapOf()
    private var evaluationJob: Job? = null

    public fun start() {
        if (evaluationJob?.isActive == true) return

        gate.onStart(System.currentTimeMillis())
        _state.value = BlockState.Unknown

        val probe = AdDomainProber(
            config = config.probeConfig,
            checker = OkHttpReachabilityChecker(
                client = OkHttpReachabilityChecker.defaultClient(config.probeConfig.timeoutMs),
            ),
        )
        val env = NetworkEnvironmentSource(
            reader = AndroidNetworkEnvironmentReader(config.context),
            registrar = AndroidNetworkChangeRegistrar(config.context),
        )
        builtInSources += probe
        builtInSources += env

        (builtInSources + config.signalSources).forEach(::attachSource)

        evaluationJob = scope.launch {
            aggregator.snapshot.collect { snap -> reevaluateInternal(snap) }
        }
    }

    public fun stop() {
        evaluationJob?.cancel()
        evaluationJob = null

        (builtInSources + config.signalSources).forEach { source ->
            source.stop()
            detachSource(source)
        }
        builtInSources.clear()
        aggregator.stop()
    }

    public fun reevaluate() {
        scope.launch {
            reevaluateInternal(aggregator.snapshot.value)
        }
    }

    public fun registerSignalSource(source: AdNetworkSignalSource) {
        attachSource(source)
    }

    public fun unregisterSignalSource(source: AdNetworkSignalSource) {
        source.stop()
        detachSource(source)
    }

    private fun attachSource(source: AdNetworkSignalSource) {
        aggregator.register(source)
        if (config.reporters.isNotEmpty()) {
            reporterForwardJobs[source] = scope.launch {
                source.signals.collect { signal ->
                    config.reporters.forEach { it.onSignalReceived(signal) }
                }
            }
        }
        source.start()
    }

    private fun detachSource(source: AdNetworkSignalSource) {
        aggregator.unregister(source)
        reporterForwardJobs.remove(source)?.cancel()
    }

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
