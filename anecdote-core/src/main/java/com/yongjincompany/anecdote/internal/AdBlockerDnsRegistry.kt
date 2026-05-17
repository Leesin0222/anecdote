package com.yongjincompany.anecdote.internal

/**
 * Hostnames advertised by DNS resolvers that primarily exist to block ads/trackers.
 * Matching against the Private DNS server name yields a near-certain "ad-blocker active" signal.
 *
 * Entries beginning with "." are matched as suffix patterns (e.g., ".nextdns.io" matches
 * "abc123.dns.nextdns.io"). Other entries are matched exact (case-insensitive).
 *
 * Conservative inclusion policy: only services whose primary product is ad/tracker blocking,
 * or configurable resolvers where the user-chosen profile commonly enables ad blocking.
 */
internal object AdBlockerDnsRegistry {
    val BUILTIN_SIGNATURES: List<String> = listOf(
        // AdGuard DNS — explicit ad/tracker filtering. Excludes "unfiltered.adguard-dns.com".
        "dns.com",
        "dns.adguard-dns.com",
        "family.adguard-dns.com",
        // NextDNS — per-profile resolvers; users overwhelmingly enable ad/tracker filters.
        ".nextdns.io",
        // Mullvad — ad-blocking specific endpoints.
        "adblock.dns.mullvad.net",
        "extended.dns.mullvad.net",
        // Control D — configurable resolver, ad/tracker filtering is the headline use case.
        ".controld.com",
        // LibreDNS ad-blocking profile.
        "doh.libredns.gr",
    )

    fun looksLikeAdBlocker(
        server: String?,
        extraSignatures: Iterable<String> = emptyList(),
    ): Boolean {
        if (server.isNullOrBlank()) return false
        val s = server.trim().lowercase()
        return (BUILTIN_SIGNATURES.asSequence() + extraSignatures.asSequence()).any { sig ->
            val sigLower = sig.lowercase()
            if (sigLower.startsWith(".")) {
                s.endsWith(sigLower) || s == sigLower.removePrefix(".")
            } else {
                s == sigLower
            }
        }
    }
}
