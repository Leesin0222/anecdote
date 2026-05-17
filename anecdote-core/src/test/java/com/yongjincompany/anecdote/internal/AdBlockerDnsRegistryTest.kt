package com.yongjincompany.anecdote.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerDnsRegistryTest {

    @Test
    fun `null and blank inputs return false`() {
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker(null))
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker(""))
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("   "))
    }

    @Test
    fun `exact AdGuard DNS matches`() {
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("dns.adguard-dns.com"))
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("DNS.AdGuard-DNS.COM"))
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("family.adguard-dns.com"))
    }

    @Test
    fun `suffix patterns match subdomains`() {
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("abc123.dns.nextdns.io"))
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("p0.freedns.controld.com"))
        // Bare suffix root should still match.
        assertTrue(AdBlockerDnsRegistry.looksLikeAdBlocker("nextdns.io"))
    }

    @Test
    fun `unrelated servers do not match`() {
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("dns.google"))
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("one.one.one.one"))
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("dns.example.com"))
        // ISP-provided resolver names should not produce false positives.
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("kt.com"))
    }

    @Test
    fun `extra signatures extend matcher`() {
        // Built-in matcher would reject this, but extras add it.
        assertFalse(AdBlockerDnsRegistry.looksLikeAdBlocker("dns.my-custom-blocker.com"))
        assertTrue(
            AdBlockerDnsRegistry.looksLikeAdBlocker(
                server = "dns.my-custom-blocker.com",
                extraSignatures = listOf("dns.my-custom-blocker.com"),
            ),
        )
    }

    @Test
    fun `extra suffix patterns work the same way`() {
        assertTrue(
            AdBlockerDnsRegistry.looksLikeAdBlocker(
                server = "abc.custom-dns.example",
                extraSignatures = listOf(".custom-dns.example"),
            ),
        )
    }
}
