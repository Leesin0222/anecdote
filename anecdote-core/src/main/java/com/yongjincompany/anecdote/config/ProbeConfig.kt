package com.yongjincompany.anecdote.config

public class ProbeConfig internal constructor(
    public val timeoutMs: Long,
    public val gracePeriodMs: Long,
    public val adDomains: List<String>,
    public val controlDomains: List<String>,
    /**
     * Backoff sequence (in ms) between probe cycles. The Nth cycle waits sequence[min(N, size-1)].
     * The first delay applies after the initial cycle, so the loop probes immediately on start,
     * then steps through the sequence and pins at the last value.
     */
    public val intervalSequenceMs: List<Long>,
) {
    /**
     * Compatibility shim — historically tests/callers set a single fixed interval.
     * If a caller supplied a single-element sequence we expose it here; otherwise this
     * reflects the steady-state (final) interval.
     */
    public val intervalMs: Long get() = intervalSequenceMs.last()

    public class Builder {
        public var timeoutMs: Long = 3_000L
        public var gracePeriodMs: Long = 5_000L
        public var adDomains: List<String> = DEFAULT_AD_DOMAINS
        public var controlDomains: List<String> = DEFAULT_CONTROL_DOMAINS
        public var intervalSequenceMs: List<Long> = DEFAULT_INTERVAL_SEQUENCE_MS

        /**
         * Convenience setter for a fixed interval (no backoff). Mostly for tests
         * and callers that don't need adaptive behavior.
         */
        public var intervalMs: Long
            get() = intervalSequenceMs.last()
            set(value) {
                intervalSequenceMs = listOf(value)
            }

        internal fun build(): ProbeConfig {
            require(intervalSequenceMs.isNotEmpty()) {
                "intervalSequenceMs must contain at least one entry"
            }
            require(intervalSequenceMs.all { it > 0 }) {
                "intervalSequenceMs entries must be positive"
            }
            return ProbeConfig(
                timeoutMs = timeoutMs,
                gracePeriodMs = gracePeriodMs,
                adDomains = adDomains,
                controlDomains = controlDomains,
                intervalSequenceMs = intervalSequenceMs,
            )
        }
    }

    public companion object {
        /**
         * Probe domains spanning the dominant mobile ad networks. Endpoint hostnames are
         * factual addresses observable from public network traffic, not copyrighted content.
         */
        /**
         * Globally-relevant probe domains spanning the dominant mobile ad networks.
         * Keep this list region-neutral; region-specific networks live in [AdNetworkPacks].
         *
         * Endpoint hostnames are factual addresses observable from public network traffic.
         */
        public val DEFAULT_AD_DOMAINS: List<String> = listOf(
            // Google AdMob / DoubleClick
            "pagead2.googlesyndication.com",
            "googleads.g.doubleclick.net",
            "tpc.googlesyndication.com",
            // Meta (Audience Network / Pixel CDN)
            "connect.facebook.net",
            // Unity Ads
            "config.unityads.unity3d.com",
            // AppLovin
            "ms.applovin.com",
            // ironSource (LevelPlay)
            "init.supersonicads.com",
            // Pangle (ByteDance / TikTok)
            "is.pangle.io",
        )

        public val DEFAULT_CONTROL_DOMAINS: List<String> = listOf(
            "www.google.com",
            "www.gstatic.com",
        )

        /**
         * Region-specific ad network packs. Opt-in by appending to [DEFAULT_AD_DOMAINS]:
         *
         * ```
         * probe {
         *     adDomains = ProbeConfig.DEFAULT_AD_DOMAINS + ProbeConfig.AdNetworkPacks.KOREA
         * }
         * ```
         *
         * These are intentionally NOT in the global default to avoid probing irrelevant
         * domains from regions the app doesn't serve. Contributions for additional regions
         * are welcome — open a PR with the rationale and SDK endpoints documented.
         */
        public object AdNetworkPacks {
            /**
             * Korean mobile ad/reward networks dominant in 앱테크 (cashback/reward) apps.
             * Bypassed by users running KR-specific filter lists which the global pack misses.
             */
            public val KOREA: List<String> = listOf(
                // Buzzvil — dominant SDK for KR reward/cashback apps.
                "ssp.buzzvil.com",
                // TNK Factory — CPI/CPS reward ad platform.
                "m.tnkad.net",
                // IGAWorks Adpopcorn — reward ad / offerwall.
                "m.adpopcorn.com",
                // Kakao AdFit — general-purpose KR display ads.
                "display.ad.daum.net",
                // Naver GFA (Game For Advertising) — mobile app ad network.
                "gfa.naver.com",
            )
        }

        /**
         * Adaptive backoff: fast at session start to converge quickly, then taper to
         * a low-cost steady state. Reset to the head on network environment changes.
         */
        public val DEFAULT_INTERVAL_SEQUENCE_MS: List<Long> = listOf(
            5_000L,
            15_000L,
            30_000L,
            60_000L,
            120_000L,
            300_000L,
        )
    }
}
