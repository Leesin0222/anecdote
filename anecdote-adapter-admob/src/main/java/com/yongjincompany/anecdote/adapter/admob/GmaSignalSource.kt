package com.yongjincompany.anecdote.adapter.admob

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signal source for Google Mobile Ads (AdMob, GMA Next-Gen SDK).
 *
 * Emits an `admob` signal for every ad load attempt, plus per-mediation-adapter signals
 * parsed from [ResponseInfo.getAdapterResponses] so that mediated networks (AppLovin,
 * Unity, ironSource, Meta, Pangle, …) contribute independent evidence to the aggregator.
 *
 * Pass a [ConsentChecker] (e.g. [UmpConsentChecker]) to filter out `LoadFailed` signals
 * that originate from GDPR/CCPA consent state rather than ad blocking.
 *
 * Banner / Native:
 * ```
 * adView.adListener = gmaSource.adListener(
 *     delegate = myExistingListener,
 *     responseInfoProvider = { adView.responseInfo },
 * )
 * ```
 *
 * Interstitial:
 * ```
 * InterstitialAd.load(ctx, unitId, request, gmaSource.interstitialLoadCallback())
 * ```
 *
 * Rewarded:
 * ```
 * RewardedAd.load(ctx, unitId, request, gmaSource.rewardedLoadCallback())
 * ```
 */
public class GmaSignalSource @JvmOverloads public constructor(
    private val consentChecker: ConsentChecker? = null,
) : AdNetworkSignalSource {

    override val networkId: String = NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    override fun start() {
        // No-op: signals flow from wrapped callbacks on demand.
    }

    override fun stop() {
        // No-op.
    }

    @JvmOverloads
    public fun adListener(
        delegate: AdListener? = null,
        responseInfoProvider: (() -> ResponseInfo?)? = null,
    ): AdListener = object : AdListener() {
        override fun onAdClicked() {
            delegate?.onAdClicked()
        }

        override fun onAdClosed() {
            delegate?.onAdClosed()
        }

        override fun onAdImpression() {
            delegate?.onAdImpression()
        }

        override fun onAdOpened() {
            delegate?.onAdOpened()
        }

        override fun onAdLoaded() {
            emitLoadSucceeded(responseInfoProvider?.invoke())
            delegate?.onAdLoaded()
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            emitLoadFailed(error)
            delegate?.onAdFailedToLoad(error)
        }
    }

    @JvmOverloads
    public fun interstitialLoadCallback(
        delegate: InterstitialAdLoadCallback? = null,
    ): InterstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
            emitLoadSucceeded(ad.responseInfo)
            delegate?.onAdLoaded(ad)
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            emitLoadFailed(error)
            delegate?.onAdFailedToLoad(error)
        }
    }

    @JvmOverloads
    public fun rewardedLoadCallback(
        delegate: RewardedAdLoadCallback? = null,
    ): RewardedAdLoadCallback = object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
            emitLoadSucceeded(ad.responseInfo)
            delegate?.onAdLoaded(ad)
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            emitLoadFailed(error)
            delegate?.onAdFailedToLoad(error)
        }
    }

    private fun emitLoadSucceeded(responseInfo: ResponseInfo?) {
        val now = System.currentTimeMillis()
        _signals.tryEmit(
            AdNetworkSignal.LoadSucceeded(
                networkId = networkId,
                timestamp = now,
            ),
        )
        emitAdapterSignals(responseInfo, now)
    }

    private fun emitLoadFailed(error: LoadAdError) {
        if (consentChecker?.isConsentIssue() == true) return
        val now = System.currentTimeMillis()
        _signals.tryEmit(
            AdNetworkSignal.LoadFailed(
                networkId = networkId,
                timestamp = now,
                errorType = error.toAnecdoteErrorType(),
                rawErrorCode = error.code,
                rawErrorMessage = error.message,
            ),
        )
        emitAdapterSignals(error.responseInfo, now)
    }

    private fun emitAdapterSignals(responseInfo: ResponseInfo?, timestamp: Long) {
        val adapters = responseInfo?.adapterResponses ?: return
        for (adapter in adapters) {
            val mediatedId = GmaMediationMapping.networkId(adapter.adapterClassName)
            if (mediatedId == networkId) continue
            val adapterError = adapter.adError
            if (adapterError == null) {
                _signals.tryEmit(
                    AdNetworkSignal.LoadSucceeded(
                        networkId = mediatedId,
                        timestamp = timestamp,
                    ),
                )
            } else {
                _signals.tryEmit(
                    AdNetworkSignal.LoadFailed(
                        networkId = mediatedId,
                        timestamp = timestamp,
                        errorType = adapterError.toAnecdoteErrorType(),
                        rawErrorCode = adapterError.code,
                        rawErrorMessage = adapterError.message,
                    ),
                )
            }
        }
    }

    public companion object {
        public const val NETWORK_ID: String = "admob"
        private const val BUFFER_CAPACITY = 64
    }
}
