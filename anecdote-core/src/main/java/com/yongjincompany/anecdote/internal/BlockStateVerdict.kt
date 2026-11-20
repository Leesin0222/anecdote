package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.BlockState

/**
 * True when both states carry the same verdict (kind + confidence). The attached signal list
 * changes on every snapshot, so plain `==` would treat each re-evaluation as a transition —
 * burning through `maxEvaluationsPerSession` and re-notifying reporters with the same verdict.
 */
internal fun BlockState.hasSameVerdictAs(other: BlockState): Boolean = when (this) {
    BlockState.Unknown -> other == BlockState.Unknown
    BlockState.NotBlocked -> other == BlockState.NotBlocked
    is BlockState.Suspected -> other is BlockState.Suspected && other.confidence == confidence
    is BlockState.Blocked -> other is BlockState.Blocked && other.confidence == confidence
}
