package com.yongjincompany.anecdote.adapter.admob

import com.google.android.ump.ConsentInformation

/**
 * Reports whether the current UMP / privacy-consent state would prevent ad serving
 * for reasons unrelated to ad blocking.
 *
 * When the user has not yet granted consent (GDPR / CCPA / other privacy regimes),
 * AdMob will refuse to serve ads, producing `LoadFailed` callbacks that look identical
 * to ad-blocker interference. Filtering these out keeps the detector's signal clean.
 */
public fun interface ConsentChecker {
    /**
     * @return `true` if the current ad-load failure is expected due to consent state
     *   (signals should be suppressed); `false` otherwise (proceed with normal signal emission).
     */
    public fun isConsentIssue(): Boolean
}

/**
 * [ConsentChecker] backed by a UMP [ConsentInformation]. Treats
 * [ConsentInformation.canRequestAds] `== false` as a consent issue.
 *
 * Example:
 * ```
 * val consent = UserMessagingPlatform.getConsentInformation(context)
 * val source = GmaSignalSource(consentChecker = UmpConsentChecker(consent))
 * ```
 */
public class UmpConsentChecker(
    private val consentInformation: ConsentInformation,
) : ConsentChecker {
    override fun isConsentIssue(): Boolean = !consentInformation.canRequestAds()
}
