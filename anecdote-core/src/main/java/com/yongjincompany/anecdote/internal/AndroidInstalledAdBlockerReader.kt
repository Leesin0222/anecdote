package com.yongjincompany.anecdote.internal

import android.content.Context
import android.content.pm.PackageManager

internal class AndroidInstalledAdBlockerReader(
    private val context: Context,
) : InstalledAdBlockerReader {

    override fun installed(candidates: Set<String>): Set<String> {
        val pm = context.packageManager
        return candidates.filterTo(mutableSetOf()) { pkg -> isInstalled(pm, pkg) }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        // Defensive: rare PackageManager edge cases (dead transactions, etc).
        false
    }
}
