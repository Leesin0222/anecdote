package com.yongjincompany.anecdote.adapter.admob

import org.junit.Assert.assertEquals
import org.junit.Test

class GmaMediationMappingTest {

    @Test
    fun `google mediation adapters map to canonical network id`() {
        assertEquals("applovin", GmaMediationMapping.networkId("com.google.ads.mediation.applovin.AppLovinMediationAdapter"))
        assertEquals("unity", GmaMediationMapping.networkId("com.google.ads.mediation.unity.UnityMediationAdapter"))
        assertEquals("ironsource", GmaMediationMapping.networkId("com.google.ads.mediation.ironsource.IronSourceMediationAdapter"))
        assertEquals("meta", GmaMediationMapping.networkId("com.google.ads.mediation.facebook.FacebookMediationAdapter"))
        assertEquals("pangle", GmaMediationMapping.networkId("com.google.ads.mediation.pangle.PangleMediationAdapter"))
        assertEquals("vungle", GmaMediationMapping.networkId("com.google.ads.mediation.vungle.VungleMediationAdapter"))
        assertEquals("inmobi", GmaMediationMapping.networkId("com.google.ads.mediation.inmobi.InMobiMediationAdapter"))
        assertEquals("mintegral", GmaMediationMapping.networkId("com.google.ads.mediation.mintegral.MintegralMediationAdapter"))
    }

    @Test
    fun `third-party SDK classes map to same network id`() {
        assertEquals("applovin", GmaMediationMapping.networkId("com.applovin.mediation.ads.MaxAdapter"))
        assertEquals("unity", GmaMediationMapping.networkId("com.unity3d.ads.UnityAdsAdapter"))
        assertEquals("meta", GmaMediationMapping.networkId("com.facebook.ads.AudienceNetworkAds"))
        assertEquals("pangle", GmaMediationMapping.networkId("com.bytedance.sdk.openadsdk.TTAdManager"))
    }

    @Test
    fun `default AdMob adapter maps to admob`() {
        assertEquals("admob", GmaMediationMapping.networkId("com.google.android.gms.ads.mediation.admob.AdMobAdapter"))
        assertEquals("admob", GmaMediationMapping.networkId("com.google.android.gms.ads.SomeOtherInternal"))
    }

    @Test
    fun `unrecognized class name maps to unknown`() {
        assertEquals("unknown", GmaMediationMapping.networkId("org.random.Adapter"))
        assertEquals("unknown", GmaMediationMapping.networkId(""))
    }
}
