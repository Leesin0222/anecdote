package com.yongjincompany.anecdote.internal

import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal data class NetworkEnvironmentSnapshot(
    val vpnActive: Boolean,
    val privateDnsActive: Boolean,
    val privateDnsServer: String?,
    val mcc: Int?,
)

internal fun interface NetworkEnvironmentReader {
    fun read(): NetworkEnvironmentSnapshot
}

internal fun interface NetworkChangeSubscription {
    fun unsubscribe()
}

internal fun interface NetworkChangeRegistrar {
    fun register(onChange: () -> Unit): NetworkChangeSubscription
}

internal class NetworkEnvironmentSource(
    private val reader: NetworkEnvironmentReader,
    private val registrar: NetworkChangeRegistrar,
    private val clock: Clock = SystemClock,
) : AdNetworkSignalSource {

    override val networkId: String = AdNetworkSignal.NetworkEnvironment.NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 1,
        extraBufferCapacity = 8,
        // Network-env signals are state-like; newest always wins. Dropping the oldest
        // on overflow avoids stale env leaking through while never losing the latest.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    private var subscription: NetworkChangeSubscription? = null

    override fun start() {
        if (subscription != null) return
        emitSnapshot()
        subscription = registrar.register { emitSnapshot() }
    }

    override fun stop() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun emitSnapshot() {
        val snap = reader.read()
        _signals.tryEmit(
            AdNetworkSignal.NetworkEnvironment(
                networkId = networkId,
                timestamp = clock.now(),
                vpnActive = snap.vpnActive,
                privateDnsActive = snap.privateDnsActive,
                privateDnsServer = snap.privateDnsServer,
                mcc = snap.mcc,
            ),
        )
    }
}
