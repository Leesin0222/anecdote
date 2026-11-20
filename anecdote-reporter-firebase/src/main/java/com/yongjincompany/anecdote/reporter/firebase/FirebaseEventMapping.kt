package com.yongjincompany.anecdote.reporter.firebase

import android.os.Bundle
import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.ScoreBreakdown
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlin.math.roundToLong

internal const val MAX_STRING_VALUE_LENGTH = 100

internal fun BlockState.toFirebaseParams(): Map<String, Any> = buildMap {
    put(PARAM_STATE, stateKey())
    when (this@toFirebaseParams) {
        is BlockState.Suspected -> {
            put(PARAM_CONFIDENCE, confidence.name.lowercase())
            put(PARAM_SIGNAL_COUNT, signals.size.toLong())
            breakdown?.let { putAll(it.toFirebaseParams()) }
        }
        is BlockState.Blocked -> {
            put(PARAM_CONFIDENCE, confidence.name.lowercase())
            put(PARAM_SIGNAL_COUNT, signals.size.toLong())
            breakdown?.let { putAll(it.toFirebaseParams()) }
        }
        BlockState.NotBlocked,
        BlockState.Unknown,
        -> Unit
    }
}

/** Points are rounded to whole numbers — enough resolution for dashboards, fewer bytes. */
internal fun ScoreBreakdown.toFirebaseParams(): Map<String, Any> = mapOf(
    PARAM_SCORE to total.toLong(),
    PARAM_SCORE_PROBE to probeDelta.roundToLong(),
    PARAM_SCORE_DNS_BLOCK to dnsBlock.roundToLong(),
    PARAM_SCORE_LOAD to loadFailure.roundToLong(),
    PARAM_SCORE_VPN to vpn.roundToLong(),
    PARAM_SCORE_PRIVATE_DNS to privateDns.roundToLong(),
    PARAM_SCORE_KNOWN_DNS to knownAdBlockerDns.roundToLong(),
    PARAM_SCORE_INSTALLED to installedAdBlocker.roundToLong(),
    PARAM_AMBIENT_MULTIPLIER to ambientMultiplier,
)

internal fun AdNetworkSignal.toFirebaseParams(): Map<String, Any> = buildMap {
    put(PARAM_SIGNAL_TYPE, signalTypeKey())
    put(PARAM_NETWORK_ID, networkId)
    when (this@toFirebaseParams) {
        is AdNetworkSignal.LoadFailed -> {
            put(PARAM_ERROR_TYPE, errorType.name.lowercase())
            rawErrorCode?.let { put(PARAM_RAW_ERROR_CODE, it.toLong()) }
            rawErrorMessage
                ?.take(MAX_STRING_VALUE_LENGTH)
                ?.let { put(PARAM_RAW_ERROR_MESSAGE, it) }
        }
        is AdNetworkSignal.LoadSucceeded -> Unit
        is AdNetworkSignal.ProbeResult -> {
            put(PARAM_DOMAIN, domain)
            put(PARAM_IS_CONTROL, if (isControl) 1L else 0L)
            put(PARAM_REACHABLE, if (reachable) 1L else 0L)
            latencyMs?.let { put(PARAM_LATENCY_MS, it) }
            failureReason?.let { put(PARAM_FAILURE_REASON, it.name.lowercase()) }
        }
        is AdNetworkSignal.NetworkEnvironment -> {
            put(PARAM_VPN_ACTIVE, if (vpnActive) 1L else 0L)
            put(PARAM_PRIVATE_DNS_ACTIVE, if (privateDnsActive) 1L else 0L)
            privateDnsServer
                ?.take(MAX_STRING_VALUE_LENGTH)
                ?.let { put(PARAM_PRIVATE_DNS_SERVER, it) }
            mcc?.let { put(PARAM_MCC, it.toLong()) }
        }
        is AdNetworkSignal.InstalledAdBlockers -> {
            put(PARAM_INSTALLED_BLOCKER_COUNT, packages.size.toLong())
            if (packages.isNotEmpty()) {
                packages.joinToString(separator = ",")
                    .take(MAX_STRING_VALUE_LENGTH)
                    .let { put(PARAM_INSTALLED_BLOCKER_PACKAGES, it) }
            }
        }
    }
}

internal fun Map<String, Any>.toBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in this) {
        when (value) {
            is String -> bundle.putString(key, value)
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is Int -> bundle.putLong(key, value.toLong())
            is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
            else -> error(
                "Unsupported Firebase param type for key '$key': ${value::class.simpleName}",
            )
        }
    }
    return bundle
}

private fun BlockState.stateKey(): String = when (this) {
    BlockState.Unknown -> "unknown"
    BlockState.NotBlocked -> "not_blocked"
    is BlockState.Suspected -> "suspected"
    is BlockState.Blocked -> "blocked"
}

private fun AdNetworkSignal.signalTypeKey(): String = when (this) {
    is AdNetworkSignal.LoadFailed -> "load_failed"
    is AdNetworkSignal.LoadSucceeded -> "load_succeeded"
    is AdNetworkSignal.ProbeResult -> "probe_result"
    is AdNetworkSignal.NetworkEnvironment -> "network_environment"
    is AdNetworkSignal.InstalledAdBlockers -> "installed_ad_blockers"
}

// Firebase event names (40 char max, alphanumeric + underscore, leading letter)
internal const val EVENT_STATE_CHANGED = "adblock_state_changed"
internal const val EVENT_SIGNAL = "adblock_signal"

// Parameter names (40 char max, alphanumeric + underscore, leading letter)
internal const val PARAM_STATE = "state"
internal const val PARAM_CONFIDENCE = "confidence"
internal const val PARAM_SIGNAL_COUNT = "signal_count"
internal const val PARAM_SCORE = "score"
internal const val PARAM_SCORE_PROBE = "score_probe"
internal const val PARAM_SCORE_DNS_BLOCK = "score_dns_block"
internal const val PARAM_SCORE_LOAD = "score_load"
internal const val PARAM_SCORE_VPN = "score_vpn"
internal const val PARAM_SCORE_PRIVATE_DNS = "score_private_dns"
internal const val PARAM_SCORE_KNOWN_DNS = "score_known_dns"
internal const val PARAM_SCORE_INSTALLED = "score_installed"
internal const val PARAM_AMBIENT_MULTIPLIER = "ambient_multiplier"
internal const val PARAM_SIGNAL_TYPE = "signal_type"
internal const val PARAM_NETWORK_ID = "network_id"
internal const val PARAM_ERROR_TYPE = "error_type"
internal const val PARAM_RAW_ERROR_CODE = "raw_error_code"
internal const val PARAM_RAW_ERROR_MESSAGE = "raw_error_message"
internal const val PARAM_DOMAIN = "domain"
internal const val PARAM_IS_CONTROL = "is_control"
internal const val PARAM_REACHABLE = "reachable"
internal const val PARAM_LATENCY_MS = "latency_ms"
internal const val PARAM_FAILURE_REASON = "failure_reason"
internal const val PARAM_VPN_ACTIVE = "vpn_active"
internal const val PARAM_PRIVATE_DNS_ACTIVE = "private_dns_active"
internal const val PARAM_PRIVATE_DNS_SERVER = "private_dns_server"
internal const val PARAM_MCC = "mcc"
internal const val PARAM_INSTALLED_BLOCKER_COUNT = "installed_blocker_count"
internal const val PARAM_INSTALLED_BLOCKER_PACKAGES = "installed_blocker_packages"
