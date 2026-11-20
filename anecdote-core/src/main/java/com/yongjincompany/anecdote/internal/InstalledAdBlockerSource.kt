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

internal fun interface PackageChangeSubscription {
    fun unsubscribe()
}

internal fun interface PackageChangeRegistrar {
    /**
     * Calls [onChange] whenever a package is installed, removed or replaced. The argument is
     * the affected package name, or null when the platform didn't say which.
     */
    fun register(onChange: (packageName: String?) -> Unit): PackageChangeSubscription
}

/**
 * Emits an [AdNetworkSignal.InstalledAdBlockers] on start listing every known ad-blocker
 * package present on the device, then re-scans whenever a candidate package is installed or
 * removed — so uninstalling a blocker mid-session clears the signal without an app restart.
 * A re-scan only emits when the installed set actually changed.
 */
internal class InstalledAdBlockerSource(
    private val reader: InstalledAdBlockerReader,
    private val candidates: Set<String> = InstalledAdBlockerRegistry.PACKAGES,
    private val registrar: PackageChangeRegistrar = PackageChangeRegistrar { PackageChangeSubscription { } },
    private val clock: Clock = SystemClock,
) : AdNetworkSignalSource {

    override val networkId: String = AdNetworkSignal.InstalledAdBlockers.NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    private val lock = Any()

    // Guarded by [lock].
    private var subscription: PackageChangeSubscription? = null
    private var lastEmitted: Set<String>? = null

    override fun start() {
        synchronized(lock) {
            if (subscription != null) return
            scanAndEmitLocked(force = true)
            subscription = registrar.register { pkg ->
                if (pkg == null || pkg in candidates) {
                    synchronized(lock) {
                        if (subscription != null) scanAndEmitLocked(force = false)
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            subscription?.unsubscribe()
            subscription = null
        }
    }

    private fun scanAndEmitLocked(force: Boolean) {
        val installed = runCatching { reader.installed(candidates) }.getOrDefault(emptySet())
        if (!force && installed == lastEmitted) return
        lastEmitted = installed
        _signals.tryEmit(
            AdNetworkSignal.InstalledAdBlockers(
                networkId = networkId,
                timestamp = clock.now(),
                packages = installed,
            ),
        )
    }
}
