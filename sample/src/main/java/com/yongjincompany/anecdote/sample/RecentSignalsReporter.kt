package com.yongjincompany.anecdote.sample

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.report.BlockEventReporter
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RecentSignalsReporter(
    private val maxRetained: Int = 20,
) : BlockEventReporter {

    private val _recent = MutableStateFlow<List<AdNetworkSignal>>(emptyList())
    val recent: StateFlow<List<AdNetworkSignal>> = _recent.asStateFlow()

    override fun onStateChanged(state: BlockState) {}

    override fun onSignalReceived(signal: AdNetworkSignal) {
        _recent.update { (it + signal).takeLast(maxRetained) }
    }
}
