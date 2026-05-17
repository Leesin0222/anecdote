package com.yongjincompany.anecdote.signal

public sealed class AdNetworkSignal {
    public abstract val networkId: String
    public abstract val timestamp: Long

    public data class LoadFailed(
        override val networkId: String,
        override val timestamp: Long,
        val errorType: ErrorType,
        val rawErrorCode: Int?,
        val rawErrorMessage: String?,
    ) : AdNetworkSignal()

    public data class LoadSucceeded(
        override val networkId: String,
        override val timestamp: Long,
    ) : AdNetworkSignal()

    public data class ProbeResult(
        override val networkId: String,
        override val timestamp: Long,
        val domain: String,
        val isControl: Boolean,
        val reachable: Boolean,
        val latencyMs: Long?,
    ) : AdNetworkSignal() {
        public companion object {
            public const val NETWORK_ID: String = "probe"
        }
    }

    public data class NetworkEnvironment(
        override val networkId: String,
        override val timestamp: Long,
        val vpnActive: Boolean,
        val privateDnsActive: Boolean,
        val privateDnsServer: String?,
        val mcc: Int?,
    ) : AdNetworkSignal() {
        public companion object {
            public const val NETWORK_ID: String = "env"
        }
    }

    public data class InstalledAdBlockers(
        override val networkId: String,
        override val timestamp: Long,
        val packages: Set<String>,
    ) : AdNetworkSignal() {
        public companion object {
            public const val NETWORK_ID: String = "installed_blockers"
        }
    }
}
