package com.yongjincompany.anecdote.reporter.firebase

import com.google.firebase.analytics.FirebaseAnalytics
import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.report.BlockEventReporter
import com.yongjincompany.anecdote.signal.AdNetworkSignal

/**
 * [BlockEventReporter] that forwards anecdote SDK events to Firebase Analytics.
 *
 * Event names:
 * - `adblock_state_changed` — state transitions (Unknown / NotBlocked / Suspected / Blocked)
 * - `adblock_signal` — individual signals (probe results, load failures, env changes)
 *
 * Signal events can produce significant volume (≥ one probe signal per ad domain every
 * probe interval per session). Pass `includeSignalEvents = false` to emit only state
 * transitions if signal-level observability is not needed.
 */
public class FirebaseBlockEventReporter @JvmOverloads public constructor(
    private val analytics: FirebaseAnalytics,
    private val includeSignalEvents: Boolean = true,
) : BlockEventReporter {

    override fun onStateChanged(state: BlockState) {
        analytics.logEvent(EVENT_STATE_CHANGED, state.toFirebaseParams().toBundle())
    }

    override fun onSignalReceived(signal: AdNetworkSignal) {
        if (!includeSignalEvents) return
        analytics.logEvent(EVENT_SIGNAL, signal.toFirebaseParams().toBundle())
    }
}
