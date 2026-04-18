package com.yongjincompany.anecdote.reporter.firebase

import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseEventMappingTest {

    @Test
    fun `Unknown state emits state only`() {
        val params = BlockState.Unknown.toFirebaseParams()
        assertEquals("unknown", params[PARAM_STATE])
        assertFalse(params.containsKey(PARAM_CONFIDENCE))
        assertFalse(params.containsKey(PARAM_SIGNAL_COUNT))
    }

    @Test
    fun `NotBlocked state emits state only`() {
        val params = BlockState.NotBlocked.toFirebaseParams()
        assertEquals("not_blocked", params[PARAM_STATE])
        assertFalse(params.containsKey(PARAM_CONFIDENCE))
    }

    @Test
    fun `Suspected state includes confidence and signal count`() {
        val state = BlockState.Suspected(
            confidence = Confidence.MEDIUM,
            signals = listOf(sampleProbe(), sampleProbe()),
        )
        val params = state.toFirebaseParams()
        assertEquals("suspected", params[PARAM_STATE])
        assertEquals("medium", params[PARAM_CONFIDENCE])
        assertEquals(2L, params[PARAM_SIGNAL_COUNT])
    }

    @Test
    fun `Blocked state includes confidence and signal count`() {
        val state = BlockState.Blocked(
            confidence = Confidence.HIGH,
            signals = listOf(sampleProbe()),
        )
        val params = state.toFirebaseParams()
        assertEquals("blocked", params[PARAM_STATE])
        assertEquals("high", params[PARAM_CONFIDENCE])
        assertEquals(1L, params[PARAM_SIGNAL_COUNT])
    }

    @Test
    fun `LoadFailed signal maps fields correctly`() {
        val signal = AdNetworkSignal.LoadFailed(
            networkId = "admob",
            timestamp = 100L,
            errorType = ErrorType.NO_FILL,
            rawErrorCode = 3,
            rawErrorMessage = "no fill",
        )
        val params = signal.toFirebaseParams()
        assertEquals("load_failed", params[PARAM_SIGNAL_TYPE])
        assertEquals("admob", params[PARAM_NETWORK_ID])
        assertEquals("no_fill", params[PARAM_ERROR_TYPE])
        assertEquals(3L, params[PARAM_RAW_ERROR_CODE])
        assertEquals("no fill", params[PARAM_RAW_ERROR_MESSAGE])
    }

    @Test
    fun `LoadFailed with null raw fields omits them`() {
        val signal = AdNetworkSignal.LoadFailed(
            networkId = "admob",
            timestamp = 100L,
            errorType = ErrorType.UNKNOWN,
            rawErrorCode = null,
            rawErrorMessage = null,
        )
        val params = signal.toFirebaseParams()
        assertFalse(params.containsKey(PARAM_RAW_ERROR_CODE))
        assertFalse(params.containsKey(PARAM_RAW_ERROR_MESSAGE))
    }

    @Test
    fun `LoadFailed truncates long error message to 100 chars`() {
        val longMsg = "x".repeat(500)
        val signal = AdNetworkSignal.LoadFailed(
            networkId = "admob",
            timestamp = 100L,
            errorType = ErrorType.UNKNOWN,
            rawErrorCode = null,
            rawErrorMessage = longMsg,
        )
        val params = signal.toFirebaseParams()
        val truncated = params[PARAM_RAW_ERROR_MESSAGE] as String
        assertEquals(MAX_STRING_VALUE_LENGTH, truncated.length)
    }

    @Test
    fun `LoadSucceeded signal has minimal params`() {
        val signal = AdNetworkSignal.LoadSucceeded(networkId = "admob", timestamp = 100L)
        val params = signal.toFirebaseParams()
        assertEquals("load_succeeded", params[PARAM_SIGNAL_TYPE])
        assertEquals("admob", params[PARAM_NETWORK_ID])
        assertEquals(2, params.size)
    }

    @Test
    fun `ProbeResult signal encodes booleans as 0 or 1`() {
        val signal = AdNetworkSignal.ProbeResult(
            networkId = "probe",
            timestamp = 100L,
            domain = "ad.example",
            isControl = false,
            reachable = true,
            latencyMs = 42L,
        )
        val params = signal.toFirebaseParams()
        assertEquals("probe_result", params[PARAM_SIGNAL_TYPE])
        assertEquals("ad.example", params[PARAM_DOMAIN])
        assertEquals(0L, params[PARAM_IS_CONTROL])
        assertEquals(1L, params[PARAM_REACHABLE])
        assertEquals(42L, params[PARAM_LATENCY_MS])
    }

    @Test
    fun `ProbeResult without latency omits latency param`() {
        val signal = AdNetworkSignal.ProbeResult(
            networkId = "probe",
            timestamp = 100L,
            domain = "ad.example",
            isControl = false,
            reachable = false,
            latencyMs = null,
        )
        val params = signal.toFirebaseParams()
        assertFalse(params.containsKey(PARAM_LATENCY_MS))
    }

    @Test
    fun `NetworkEnvironment signal encodes booleans and optionals`() {
        val signal = AdNetworkSignal.NetworkEnvironment(
            networkId = "env",
            timestamp = 100L,
            vpnActive = true,
            privateDnsActive = true,
            privateDnsServer = "dns.example",
            mcc = 450,
        )
        val params = signal.toFirebaseParams()
        assertEquals("network_environment", params[PARAM_SIGNAL_TYPE])
        assertEquals(1L, params[PARAM_VPN_ACTIVE])
        assertEquals(1L, params[PARAM_PRIVATE_DNS_ACTIVE])
        assertEquals("dns.example", params[PARAM_PRIVATE_DNS_SERVER])
        assertEquals(450L, params[PARAM_MCC])
    }

    @Test
    fun `NetworkEnvironment with null optionals omits them`() {
        val signal = AdNetworkSignal.NetworkEnvironment(
            networkId = "env",
            timestamp = 100L,
            vpnActive = false,
            privateDnsActive = false,
            privateDnsServer = null,
            mcc = null,
        )
        val params = signal.toFirebaseParams()
        assertFalse(params.containsKey(PARAM_PRIVATE_DNS_SERVER))
        assertFalse(params.containsKey(PARAM_MCC))
        assertEquals(0L, params[PARAM_VPN_ACTIVE])
        assertEquals(0L, params[PARAM_PRIVATE_DNS_ACTIVE])
    }

    @Test
    fun `event names conform to firebase constraints`() {
        assertTrue(EVENT_STATE_CHANGED.matches(Regex("[a-z][a-z0-9_]{0,39}")))
        assertTrue(EVENT_SIGNAL.matches(Regex("[a-z][a-z0-9_]{0,39}")))
    }

    private fun sampleProbe() = AdNetworkSignal.ProbeResult(
        networkId = "probe",
        timestamp = 1L,
        domain = "ad.example",
        isControl = false,
        reachable = false,
        latencyMs = null,
    )
}
