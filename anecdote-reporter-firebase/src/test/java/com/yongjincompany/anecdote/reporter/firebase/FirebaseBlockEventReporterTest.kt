package com.yongjincompany.anecdote.reporter.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.yongjincompany.anecdote.BlockState
import com.yongjincompany.anecdote.Confidence
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.ErrorType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class FirebaseBlockEventReporterTest {

    private val analytics: FirebaseAnalytics = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any<String>()) } just Runs
        every { anyConstructed<Bundle>().putLong(any(), any<Long>()) } just Runs
        every { anyConstructed<Bundle>().putDouble(any(), any<Double>()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkConstructor(Bundle::class)
    }

    @Test
    fun `onStateChanged logs state_changed event`() {
        val reporter = FirebaseBlockEventReporter(analytics)
        reporter.onStateChanged(BlockState.NotBlocked)

        verify(exactly = 1) { analytics.logEvent(EVENT_STATE_CHANGED, any()) }
    }

    @Test
    fun `onStateChanged logs different states without crashing`() {
        val reporter = FirebaseBlockEventReporter(analytics)
        reporter.onStateChanged(BlockState.Unknown)
        reporter.onStateChanged(BlockState.NotBlocked)
        reporter.onStateChanged(BlockState.Suspected(Confidence.LOW, emptyList()))
        reporter.onStateChanged(BlockState.Blocked(Confidence.HIGH, emptyList()))

        verify(exactly = 4) { analytics.logEvent(EVENT_STATE_CHANGED, any()) }
    }

    @Test
    fun `onSignalReceived logs signal event when enabled`() {
        val reporter = FirebaseBlockEventReporter(analytics, includeSignalEvents = true)
        val signal = AdNetworkSignal.LoadFailed(
            networkId = "admob",
            timestamp = 1L,
            errorType = ErrorType.NO_FILL,
            rawErrorCode = 3,
            rawErrorMessage = "no fill",
        )
        reporter.onSignalReceived(signal)

        verify(exactly = 1) { analytics.logEvent(EVENT_SIGNAL, any()) }
    }

    @Test
    fun `onSignalReceived skipped when includeSignalEvents is false`() {
        val reporter = FirebaseBlockEventReporter(analytics, includeSignalEvents = false)
        val signal = AdNetworkSignal.LoadSucceeded(networkId = "admob", timestamp = 1L)

        reporter.onSignalReceived(signal)
        reporter.onStateChanged(BlockState.NotBlocked)

        verify(exactly = 0) { analytics.logEvent(EVENT_SIGNAL, any()) }
        verify(exactly = 1) { analytics.logEvent(EVENT_STATE_CHANGED, any()) }
    }

    @Test
    fun `different signal types all go through signal event`() {
        val reporter = FirebaseBlockEventReporter(analytics)
        reporter.onSignalReceived(AdNetworkSignal.LoadSucceeded("admob", 1L))
        reporter.onSignalReceived(AdNetworkSignal.ProbeResult("probe", 1L, "ad.example", false, true, 10L))
        reporter.onSignalReceived(
            AdNetworkSignal.NetworkEnvironment("env", 1L, false, false, null, null)
        )

        verify(exactly = 3) { analytics.logEvent(EVENT_SIGNAL, any()) }
    }
}
