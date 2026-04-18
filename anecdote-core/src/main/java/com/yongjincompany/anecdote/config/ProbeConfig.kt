package com.yongjincompany.anecdote.config

public class ProbeConfig internal constructor(
    public val timeoutMs: Long,
    public val gracePeriodMs: Long,
    public val adDomains: List<String>,
    public val controlDomains: List<String>,
    public val intervalMs: Long,
) {
    public class Builder {
        public var timeoutMs: Long = 3_000L
        public var gracePeriodMs: Long = 5_000L
        public var adDomains: List<String> = DEFAULT_AD_DOMAINS
        public var controlDomains: List<String> = DEFAULT_CONTROL_DOMAINS
        public var intervalMs: Long = 60_000L

        internal fun build(): ProbeConfig = ProbeConfig(
            timeoutMs = timeoutMs,
            gracePeriodMs = gracePeriodMs,
            adDomains = adDomains,
            controlDomains = controlDomains,
            intervalMs = intervalMs,
        )
    }

    public companion object {
        public val DEFAULT_AD_DOMAINS: List<String> = listOf(
            "pagead2.googlesyndication.com",
            "googleads.g.doubleclick.net",
            "tpc.googlesyndication.com",
        )
        public val DEFAULT_CONTROL_DOMAINS: List<String> = listOf(
            "www.google.com",
            "www.gstatic.com",
        )
    }
}
