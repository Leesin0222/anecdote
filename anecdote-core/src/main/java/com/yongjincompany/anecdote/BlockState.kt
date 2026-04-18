package com.yongjincompany.anecdote

import com.yongjincompany.anecdote.signal.AdNetworkSignal

public sealed interface BlockState {
    public data object Unknown : BlockState
    public data object NotBlocked : BlockState

    public data class Suspected(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
    ) : BlockState

    public data class Blocked(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
    ) : BlockState
}
