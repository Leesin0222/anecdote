package com.yongjincompany.anecdote.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

internal class AndroidNetworkEnvironmentReader(
    private val context: Context,
) : NetworkEnvironmentReader {

    override fun read(): NetworkEnvironmentSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val props = active?.let { cm.getLinkProperties(it) }

        val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val privateDnsActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            props?.isPrivateDnsActive == true
        val privateDnsServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            props?.privateDnsServerName
        } else {
            null
        }
        val mcc = context.resources.configuration.mcc.takeIf { it != 0 }

        return NetworkEnvironmentSnapshot(
            vpnActive = vpnActive,
            privateDnsActive = privateDnsActive,
            privateDnsServer = privateDnsServer,
            mcc = mcc,
        )
    }
}

internal class AndroidNetworkChangeRegistrar(
    private val context: Context,
) : NetworkChangeRegistrar {

    override fun register(onChange: () -> Unit): NetworkChangeSubscription {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange()
            override fun onLost(network: Network) = onChange()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = onChange()
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = onChange()
        }
        cm.registerDefaultNetworkCallback(callback)
        return NetworkChangeSubscription { cm.unregisterNetworkCallback(callback) }
    }
}
