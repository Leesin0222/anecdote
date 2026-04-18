package com.yongjincompany.anecdote.config

import android.content.Context
import com.yongjincompany.anecdote.report.BlockEventReporter
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource

internal data class DetectorConfig(
    val context: Context,
    val probeConfig: ProbeConfig,
    val policyConfig: PolicyConfig,
    val reporters: List<BlockEventReporter>,
    val signalSources: List<AdNetworkSignalSource>,
)
