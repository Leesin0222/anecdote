package com.yongjincompany.anecdote.internal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Listens for package install/remove broadcasts. Only packages visible to the app (declared in
 * the manifest `<queries>`) are delivered on Android 11+, which is exactly the candidate set.
 */
internal class AndroidPackageChangeRegistrar(
    private val context: Context,
) : PackageChangeRegistrar {

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // pre-33 branch has no flag parameter
    override fun register(onChange: (packageName: String?) -> Unit): PackageChangeSubscription {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onChange(intent?.data?.schemeSpecificPart)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Package broadcasts come from the system, so NOT_EXPORTED still receives them.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return PackageChangeSubscription {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
