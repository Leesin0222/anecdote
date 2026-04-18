package com.yongjincompany.anecdote.reporter.logcat

import android.util.Log
import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.report.BlockEventReporter
import com.yongjincompany.anecdote.signal.AdNetworkSignal

public class LogcatBlockEventReporter(
    private val tag: String = DEFAULT_TAG,
) : BlockEventReporter {
    override fun onStateChanged(state: BlockState) {
        Log.d(tag, "state -> $state")
    }

    override fun onSignalReceived(signal: AdNetworkSignal) {
        Log.d(tag, "signal -> $signal")
    }

    public companion object {
        public const val DEFAULT_TAG: String = "anecdote"
    }
}
