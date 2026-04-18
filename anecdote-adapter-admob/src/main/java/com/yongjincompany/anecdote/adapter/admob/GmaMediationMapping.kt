package com.yongjincompany.anecdote.adapter.admob

internal object GmaMediationMapping {

    /**
     * Ordered prefix → networkId rules. First match wins. More specific Google mediation
     * prefixes come before 3rd-party SDK prefixes so that Google's bundled adapter is
     * correctly attributed to the mediated network (e.g. `com.google.ads.mediation.applovin`
     * → `applovin`).
     */
    private val RULES: List<Pair<String, String>> = listOf(
        "com.google.ads.mediation.applovin" to "applovin",
        "com.applovin" to "applovin",

        "com.google.ads.mediation.unity" to "unity",
        "com.unity3d.ads" to "unity",

        "com.google.ads.mediation.ironsource" to "ironsource",
        "com.ironsource" to "ironsource",

        "com.google.ads.mediation.facebook" to "meta",
        "com.facebook.ads" to "meta",

        "com.google.ads.mediation.pangle" to "pangle",
        "com.bytedance.sdk" to "pangle",
        "com.pangle" to "pangle",

        "com.google.ads.mediation.vungle" to "vungle",
        "com.vungle" to "vungle",

        "com.google.ads.mediation.inmobi" to "inmobi",
        "com.inmobi" to "inmobi",

        "com.google.ads.mediation.mintegral" to "mintegral",
        "com.mintegral" to "mintegral",

        "com.google.android.gms.ads.mediation.admob" to "admob",
        "com.google.android.gms.ads" to "admob",
    )

    fun networkId(adapterClassName: String): String {
        for ((prefix, id) in RULES) {
            if (adapterClassName.startsWith(prefix)) return id
        }
        return "unknown"
    }
}
