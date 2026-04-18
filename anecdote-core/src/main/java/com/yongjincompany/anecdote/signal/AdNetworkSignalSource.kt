package com.yongjincompany.anecdote.signal

import kotlinx.coroutines.flow.SharedFlow

public interface AdNetworkSignalSource {
    public val networkId: String
    public val signals: SharedFlow<AdNetworkSignal>

    public fun start()
    public fun stop()
}
