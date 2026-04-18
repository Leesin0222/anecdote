package com.yongjincompany.anecdote.report

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.signal.AdNetworkSignal

public interface BlockEventReporter {
    public fun onStateChanged(state: BlockState)
    public fun onSignalReceived(signal: AdNetworkSignal)
}
