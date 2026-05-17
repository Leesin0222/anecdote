package com.yongjincompany.anecdote.internal

/**
 * Android package names of known ad-blocking apps. Presence of any of these as an
 * installed package is a strong positive signal.
 *
 * Used to populate the <queries> declaration in AndroidManifest.xml so PackageManager
 * lookups succeed on Android 11+ without QUERY_ALL_PACKAGES.
 */
internal object InstalledAdBlockerRegistry {
    val PACKAGES: Set<String> = setOf(
        // AdGuard
        "com.adguard.android",
        "com.adguard.android.contentblocker",
        // Blokada (multiple distribution variants)
        "org.blokada.fem.fdroid",
        "org.blokada.origin.alarm",
        "org.blokada.alarm.dnschanger",
        // DNS66
        "org.jak_linux.dns66",
        // Personal DNS Filter
        "dnsfilter.android",
        // Nebulo / DNSChanger (Frostnerd)
        "com.frostnerd.smokescreen",
        "com.frostnerd.dnschanger",
        // NetGuard (firewall, commonly used for ad blocking)
        "eu.faircode.netguard",
        // RethinkDNS
        "com.celzero.bravedns",
    )
}
