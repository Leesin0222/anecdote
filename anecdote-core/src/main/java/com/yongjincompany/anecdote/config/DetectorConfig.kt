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
    /**
     * Consumer-supplied extras layered on top of the built-in DNS server matcher.
     * Matched with the same suffix/exact rules as the built-in list:
     * entries beginning with "." are suffix-matched, otherwise exact (case-insensitive).
     */
    val extraAdBlockerDnsSuffixes: Set<String>,
    /**
     * Consumer-supplied extras layered on top of the built-in installed-package registry.
     * Package names listed here will also be checked against PackageManager; they should
     * additionally be declared in the host app's AndroidManifest <queries>.
     */
    val extraInstalledAdBlockerPackages: Set<String>,
)
