package com.yongjincompany.anecdote.adapter.admob

import app.cash.turbine.test
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdapterResponseInfo
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GmaSignalSourceTest {

    private fun adError(code: Int, message: String = "mocked"): AdError = mockk {
        every { this@mockk.code } returns code
        every { this@mockk.message } returns message
    }

    private fun loadAdError(
        code: Int,
        message: String = "mocked",
        responseInfo: ResponseInfo? = null,
    ): LoadAdError = mockk {
        every { this@mockk.code } returns code
        every { this@mockk.message } returns message
        every { this@mockk.responseInfo } returns responseInfo
    }

    private fun adapterResponse(
        className: String,
        error: AdError? = null,
    ): AdapterResponseInfo = mockk {
        every { adapterClassName } returns className
        every { adError } returns error
    }

    private fun responseInfo(adapters: List<AdapterResponseInfo>): ResponseInfo = mockk {
        every { adapterResponses } returns adapters
    }

    @Test
    fun `adListener onAdLoaded emits LoadSucceeded for admob`() = runTest {
        val source = GmaSignalSource()
        val listener = source.adListener()

        source.signals.test {
            listener.onAdLoaded()
            val signal = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(GmaSignalSource.NETWORK_ID, signal.networkId)
            assertTrue(signal.timestamp > 0L)
        }
    }

    @Test
    fun `adListener onAdFailedToLoad emits LoadFailed with mapped error`() = runTest {
        val source = GmaSignalSource()
        val listener = source.adListener()
        val error = loadAdError(AdRequest.ERROR_CODE_NO_FILL, "no fill")

        source.signals.test {
            listener.onAdFailedToLoad(error)
            val signal = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals(GmaSignalSource.NETWORK_ID, signal.networkId)
            assertEquals(ErrorType.NO_FILL, signal.errorType)
            assertEquals(AdRequest.ERROR_CODE_NO_FILL, signal.rawErrorCode)
            assertEquals("no fill", signal.rawErrorMessage)
        }
    }

    @Test
    fun `adListener forwards all callbacks to delegate`() {
        val delegate = mockk<AdListener>(relaxed = true)
        val source = GmaSignalSource()
        val listener = source.adListener(delegate)
        val error = loadAdError(AdRequest.ERROR_CODE_NETWORK_ERROR)

        listener.onAdClicked()
        listener.onAdClosed()
        listener.onAdImpression()
        listener.onAdOpened()
        listener.onAdLoaded()
        listener.onAdFailedToLoad(error)

        verify(exactly = 1) {
            delegate.onAdClicked()
            delegate.onAdClosed()
            delegate.onAdImpression()
            delegate.onAdOpened()
            delegate.onAdLoaded()
            delegate.onAdFailedToLoad(error)
        }
    }

    @Test
    fun `adListener uses responseInfoProvider on success to expand mediation chain`() = runTest {
        val ri = responseInfo(
            listOf(
                adapterResponse(
                    "com.google.ads.mediation.applovin.AppLovinMediationAdapter",
                    error = adError(AdRequest.ERROR_CODE_NO_FILL),
                ),
                adapterResponse("com.google.ads.mediation.unity.UnityMediationAdapter", error = null),
            ),
        )
        val source = GmaSignalSource()
        val listener = source.adListener(responseInfoProvider = { ri })

        source.signals.test {
            listener.onAdLoaded()
            // Order: admob overall → applovin failed → unity succeeded
            assertEquals(GmaSignalSource.NETWORK_ID, (awaitItem() as AdNetworkSignal.LoadSucceeded).networkId)
            val applovin = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals("applovin", applovin.networkId)
            assertEquals(ErrorType.NO_FILL, applovin.errorType)
            val unity = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals("unity", unity.networkId)
        }
    }

    @Test
    fun `interstitialLoadCallback expands mediation chain on success`() = runTest {
        val ri = responseInfo(
            listOf(
                adapterResponse(
                    "com.google.ads.mediation.ironsource.IronSourceMediationAdapter",
                    error = adError(AdRequest.ERROR_CODE_NETWORK_ERROR),
                ),
                adapterResponse("com.google.ads.mediation.applovin.AppLovinMediationAdapter", error = null),
            ),
        )
        val ad = mockk<InterstitialAd> {
            every { responseInfo } returns ri
        }
        val source = GmaSignalSource()
        val callback = source.interstitialLoadCallback()

        source.signals.test {
            callback.onAdLoaded(ad)
            val overall = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(GmaSignalSource.NETWORK_ID, overall.networkId)
            val ironsource = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals("ironsource", ironsource.networkId)
            assertEquals(ErrorType.NETWORK_ERROR, ironsource.errorType)
            val applovin = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals("applovin", applovin.networkId)
        }
    }

    @Test
    fun `onAdFailedToLoad expands mediation chain from LoadAdError responseInfo`() = runTest {
        val ri = responseInfo(
            listOf(
                adapterResponse(
                    "com.google.ads.mediation.applovin.AppLovinMediationAdapter",
                    error = adError(AdRequest.ERROR_CODE_NO_FILL),
                ),
                adapterResponse(
                    "com.google.ads.mediation.unity.UnityMediationAdapter",
                    error = adError(AdRequest.ERROR_CODE_NETWORK_ERROR),
                ),
            ),
        )
        val error = loadAdError(
            code = AdRequest.ERROR_CODE_NO_FILL,
            message = "all networks exhausted",
            responseInfo = ri,
        )
        val source = GmaSignalSource()
        val listener = source.adListener()

        source.signals.test {
            listener.onAdFailedToLoad(error)
            val overall = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals(GmaSignalSource.NETWORK_ID, overall.networkId)
            val applovin = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals("applovin", applovin.networkId)
            assertEquals(ErrorType.NO_FILL, applovin.errorType)
            val unity = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals("unity", unity.networkId)
            assertEquals(ErrorType.NETWORK_ERROR, unity.errorType)
        }
    }

    @Test
    fun `adapterResponses containing admob adapter do not double-emit admob signal`() = runTest {
        val ri = responseInfo(
            listOf(
                adapterResponse("com.google.android.gms.ads.mediation.admob.AdMobAdapter", error = null),
            ),
        )
        val ad = mockk<InterstitialAd> {
            every { responseInfo } returns ri
        }
        val source = GmaSignalSource()
        val callback = source.interstitialLoadCallback()

        source.signals.test {
            callback.onAdLoaded(ad)
            val overall = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(GmaSignalSource.NETWORK_ID, overall.networkId)
            expectNoEvents() // admob adapter was skipped
        }
    }

    @Test
    fun `rewardedLoadCallback emits admob-only when responseInfo has no adapters`() = runTest {
        val ad = mockk<RewardedAd> {
            every { responseInfo } returns responseInfo(emptyList())
        }
        val source = GmaSignalSource()
        val callback = source.rewardedLoadCallback()

        source.signals.test {
            callback.onAdLoaded(ad)
            val overall = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(GmaSignalSource.NETWORK_ID, overall.networkId)
            expectNoEvents()
        }
    }

    @Test
    fun `interstitial and rewarded callbacks forward to delegate`() = runTest {
        val interDelegate = mockk<InterstitialAdLoadCallback>(relaxed = true)
        val rewardDelegate = mockk<RewardedAdLoadCallback>(relaxed = true)
        val emptyRi = responseInfo(emptyList())
        val interAd = mockk<InterstitialAd> { every { responseInfo } returns emptyRi }
        val rewardAd = mockk<RewardedAd> { every { responseInfo } returns emptyRi }
        val source = GmaSignalSource()

        source.interstitialLoadCallback(interDelegate).onAdLoaded(interAd)
        source.rewardedLoadCallback(rewardDelegate).onAdLoaded(rewardAd)

        verify(exactly = 1) {
            interDelegate.onAdLoaded(interAd)
            rewardDelegate.onAdLoaded(rewardAd)
        }
    }

    @Test
    fun `start and stop are no-op`() {
        val source = GmaSignalSource()
        source.start()
        source.start()
        source.stop()
        source.stop()
    }

    @Test
    fun `consent issue suppresses LoadFailed but not LoadSucceeded`() = runTest {
        val consentChecker = ConsentChecker { true } // always flag as consent issue
        val source = GmaSignalSource(consentChecker = consentChecker)
        val listener = source.adListener()

        source.signals.test {
            listener.onAdFailedToLoad(loadAdError(AdRequest.ERROR_CODE_NO_FILL))
            expectNoEvents()

            listener.onAdLoaded()
            val ok = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(GmaSignalSource.NETWORK_ID, ok.networkId)
        }
    }

    @Test
    fun `consent checker returning false lets LoadFailed through`() = runTest {
        val consentChecker = ConsentChecker { false }
        val source = GmaSignalSource(consentChecker = consentChecker)
        val listener = source.adListener()

        source.signals.test {
            listener.onAdFailedToLoad(loadAdError(AdRequest.ERROR_CODE_NETWORK_ERROR))
            val failed = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals(ErrorType.NETWORK_ERROR, failed.errorType)
        }
    }

    @Test
    fun `consent issue also suppresses mediation chain expansion`() = runTest {
        val consentChecker = ConsentChecker { true }
        val ri = responseInfo(
            listOf(
                adapterResponse(
                    "com.google.ads.mediation.applovin.AppLovinMediationAdapter",
                    error = adError(AdRequest.ERROR_CODE_NO_FILL),
                ),
            ),
        )
        val error = loadAdError(AdRequest.ERROR_CODE_NO_FILL, responseInfo = ri)
        val source = GmaSignalSource(consentChecker = consentChecker)
        val listener = source.adListener()

        source.signals.test {
            listener.onAdFailedToLoad(error)
            expectNoEvents()
        }
    }
}
