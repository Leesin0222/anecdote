package com.yongjincompany.anecdote

import com.yongjincompany.anecdote.signal.AdNetworkSignal

public sealed interface BlockState {
    public data object Unknown : BlockState
    public data object NotBlocked : BlockState

    public data class Suspected(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
        /** How the score was built. Null only when constructed outside the SDK. */
        val breakdown: ScoreBreakdown? = null,
    ) : BlockState

    public data class Blocked(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
        /** How the score was built. Null only when constructed outside the SDK. */
        val breakdown: ScoreBreakdown? = null,
    ) : BlockState
}
