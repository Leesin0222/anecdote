package com.yongjincompany.anecdote.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yongjincompany.anecdote.AdBlockDetector
import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.reporter.logcat.LogcatBlockEventReporter
import com.yongjincompany.anecdote.sample.ui.theme.AnecdoteTheme
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.CustomSignalSource
import com.yongjincompany.anecdote.signal.ErrorType
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private lateinit var detector: AdBlockDetector
    private val simulatedSource = CustomSignalSource(networkId = "sample-sim")
    private val recentReporter = RecentSignalsReporter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        detector = AdBlockDetector.Builder(applicationContext)
            .probe {
                timeoutMs = 3_000
                gracePeriodMs = 3_000
                intervalMs = 15_000
            }
            .policy {
                thresholdSuspected = 40
                thresholdBlocked = 70
            }
            .addReporter(LogcatBlockEventReporter())
            .addReporter(recentReporter)
            .addSignalSource(simulatedSource)
            .build()

        detector.start()

        setContent {
            AnecdoteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SampleScreen(
                        stateFlow = detector.state,
                        recentFlow = recentReporter.recent,
                        onSimulateFailure = {
                            simulatedSource.emitLoadFailed(
                                errorType = ErrorType.NETWORK_ERROR,
                                rawErrorMessage = "simulated",
                            )
                        },
                        onSimulateSuccess = { simulatedSource.emitLoadSucceeded() },
                        onReevaluate = { detector.reevaluate() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stop()
    }
}

@Composable
private fun SampleScreen(
    stateFlow: StateFlow<BlockState>,
    recentFlow: StateFlow<List<AdNetworkSignal>>,
    onSimulateFailure: () -> Unit,
    onSimulateSuccess: () -> Unit,
    onReevaluate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by stateFlow.collectAsState()
    val recent by recentFlow.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StateCard(state = state)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSimulateFailure) { Text("Fail") }
            Button(onClick = onSimulateSuccess) { Text("Success") }
            Button(onClick = onReevaluate) { Text("Reevaluate") }
        }

        HorizontalDivider()

        Text("Recent signals (${recent.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(recent.reversed()) { signal ->
                SignalRow(signal)
            }
        }
    }
}

@Composable
private fun StateCard(state: BlockState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("anecdote sample", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("State: ${state.label()}", style = MaterialTheme.typography.bodyLarge)
            val confidence = state.confidenceOrNull()
            if (confidence != null) {
                Text("Confidence: ${confidence.name}", style = MaterialTheme.typography.bodyMedium)
            }
            val signalCount = state.signalCount()
            if (signalCount > 0) {
                Text("Contributing signals: $signalCount", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SignalRow(signal: AdNetworkSignal) {
    val summary = when (signal) {
        is AdNetworkSignal.LoadFailed ->
            "LoadFailed [${signal.networkId}] ${signal.errorType}"
        is AdNetworkSignal.LoadSucceeded ->
            "LoadSucceeded [${signal.networkId}]"
        is AdNetworkSignal.ProbeResult ->
            "Probe ${signal.domain} reachable=${signal.reachable} ctrl=${signal.isControl}"
        is AdNetworkSignal.NetworkEnvironment ->
            "Env vpn=${signal.vpnActive} pDns=${signal.privateDnsActive} mcc=${signal.mcc}"
    }
    Text(summary, style = MaterialTheme.typography.bodySmall)
}

private fun BlockState.label(): String = when (this) {
    BlockState.Unknown -> "Unknown"
    BlockState.NotBlocked -> "NotBlocked"
    is BlockState.Suspected -> "Suspected"
    is BlockState.Blocked -> "Blocked"
}

private fun BlockState.confidenceOrNull(): Confidence? = when (this) {
    is BlockState.Suspected -> confidence
    is BlockState.Blocked -> confidence
    else -> null
}

private fun BlockState.signalCount(): Int = when (this) {
    is BlockState.Suspected -> signals.size
    is BlockState.Blocked -> signals.size
    else -> 0
}
