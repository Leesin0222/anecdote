package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal fun interface InstalledAdBlockerReader {
    /** Returns the subset of [candidates] that are installed on this device. */
    fun installed(candidates: Set<String>): Set<String>
}

/**
 * Emits an [AdNetworkSignal.InstalledAdBlockers] once on start, listing every known
 * ad-blocker package present on the device. The set is stable across a session so we
 * don't re-poll.
 */
internal class InstalledAdBlockerSource(
    private val reader: InstalledAdBlockerReader,
    private val candidates: Set<String> = InstalledAdBlockerRegistry.PACKAGES,
    private val clock: Clock = SystemClock,
) : AdNetworkSignalSource {

    override val networkId: String = AdNetworkSignal.InstalledAdBlockers.NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    private var emitted: Boolean = false

    override fun start() {
        if (emitted) return
        emitted = true
        val installed = runCatching { reader.installed(candidates) }.getOrDefault(emptySet())
        _signals.tryEmit(
            AdNetworkSignal.InstalledAdBlockers(
                networkId = networkId,
                timestamp = clock.now(),
                packages = installed,
            ),
        )
    }

    override fun stop() {
        // Stateless; nothing to release.
    }
}
