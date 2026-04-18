package com.yongjincompany.anecdote.signal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * User-injectable signal source. Use when no built-in adapter exists for your ad network
 * or analytics stack, or when you want to feed synthetic signals (e.g. from server-side
 * hints or experiment flags).
 *
 * Registration:
 * ```
 * val customSource = CustomSignalSource(networkId = "my-network")
 * detector.registerSignalSource(customSource)
 *
 * // from ad load callbacks:
 * customSource.emitLoadFailed(ErrorType.NETWORK_ERROR, rawErrorCode = 42)
 * ```
 *
 * `networkId` must be unique across all sources. Built-in reserved values:
 * `"probe"`, `"env"`. Adapter modules use their own stable ids (`"admob"`, `"applovin"`, …).
 */
public class CustomSignalSource(
    override val networkId: String,
) : AdNetworkSignalSource {

    init {
        require(networkId.isNotBlank()) { "networkId must not be blank" }
        require(networkId !in RESERVED_NETWORK_IDS) {
            "networkId '$networkId' is reserved for built-in sources"
        }
    }

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    override fun start() {
        // No-op: signals are pushed on demand.
    }

    override fun stop() {
        // No-op.
    }

    /**
     * Push an arbitrary signal. Returns `true` if accepted, `false` if the buffer is full
     * (caller emitted faster than the detector could consume — generally indicates a bug).
     */
    public fun emit(signal: AdNetworkSignal): Boolean = _signals.tryEmit(signal)

    /**
     * Record an ad load failure with the provided classification.
     * Timestamp uses `System.currentTimeMillis()`.
     */
    @JvmOverloads
    public fun emitLoadFailed(
        errorType: ErrorType,
        rawErrorCode: Int? = null,
        rawErrorMessage: String? = null,
    ): Boolean = emit(
        AdNetworkSignal.LoadFailed(
            networkId = networkId,
            timestamp = System.currentTimeMillis(),
            errorType = errorType,
            rawErrorCode = rawErrorCode,
            rawErrorMessage = rawErrorMessage,
        )
    )

    /** Record a successful ad load. */
    public fun emitLoadSucceeded(): Boolean = emit(
        AdNetworkSignal.LoadSucceeded(
            networkId = networkId,
            timestamp = System.currentTimeMillis(),
        )
    )

    public companion object {
        private const val BUFFER_CAPACITY = 64
        private val RESERVED_NETWORK_IDS: Set<String> = setOf(
            AdNetworkSignal.ProbeResult.NETWORK_ID,
            AdNetworkSignal.NetworkEnvironment.NETWORK_ID,
        )
    }
}
