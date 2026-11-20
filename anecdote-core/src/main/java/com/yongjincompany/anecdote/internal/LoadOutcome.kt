package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.ErrorType

/**
 * How a single ad load result should count toward the load-failure rate.
 *
 * Only transport-level failures are evidence of blocking. A NO_FILL or INVALID_REQUEST
 * response proves the ad server was reached, so it counts as a non-failed attempt rather
 * than a failure — otherwise apps with naturally low fill rates (reward/offerwall
 * inventory) would accumulate a blocking score without any blocker present.
 */
internal enum class LoadOutcome {
    /** Request never reached the ad server — consistent with blocking. */
    BLOCK_INDICATIVE,

    /** Ad server answered (even with no ad) — evidence the network path is open. */
    SERVER_REACHED,

    /** Cannot tell from the error alone; excluded from the rate entirely. */
    AMBIGUOUS,
}

internal fun ErrorType.loadOutcome(): LoadOutcome = when (this) {
    ErrorType.NETWORK_ERROR,
    ErrorType.TIMEOUT,
    ErrorType.DNS_RESOLUTION_FAILED,
    -> LoadOutcome.BLOCK_INDICATIVE

    ErrorType.NO_FILL,
    ErrorType.INVALID_REQUEST,
    -> LoadOutcome.SERVER_REACHED

    ErrorType.INTERNAL_ERROR,
    ErrorType.UNKNOWN,
    -> LoadOutcome.AMBIGUOUS
}
