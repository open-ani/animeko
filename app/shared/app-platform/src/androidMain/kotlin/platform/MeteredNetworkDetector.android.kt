/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.utils.logging.logger


@SuppressLint("MissingPermission")
private class AndroidMeteredNetworkDetector(
    private val context: Context
) : MeteredNetworkDetector, BroadcastReceiver() {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val logger by lazy { logger<AndroidMeteredNetworkDetector>() }

    private val flow = MutableStateFlow(getCurrentIsMetered())
    override val isMeteredNetworkFlow: Flow<Boolean> get() = flow
    
    // Create a NetworkCallback to detect network changes
    // private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    //     override fun onAvailable(network: Network) { // 连接 WiFi
    //         flow.tryEmit(getCurrentIsMetered())
    //     }

    //     override fun onLost(network: Network) { // 断开 WiFi
    //         flow.tryEmit(getCurrentIsMetered())
    //     }

    //     // WiFi 设置变更 (设置为计费网络)
    //     // 连接/断开 WiFi 不会触发 onCapabilitiesChanged
    //     override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
    //         val isMetered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    //         log { "onCapabilitiesChanged: isMetered=$isMetered" }
    //         flow.tryEmit(isMetered)
    //     }
    // }

    init {
        // Register the NetworkCallback instead of using BroadcastReceiver
        // val networkRequest = NetworkRequest.Builder()
        //     .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        //     .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        //     .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        //     .build()
        // connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        @Suppress("DEPRECATION")
        context.registerReceiver(this, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        // Emit the first value
        flow.value = getCurrentIsMetered()
    }

    override fun onReceive(context: android.content.Context?, intent: Intent?) {
        flow.value = getCurrentIsMetered()
    }

    private fun getCurrentIsMetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        // Return whether the network is metered or not
        val isMetered = !activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        log { "getCurrentIsMetered: isMetered=$isMetered" }
        return isMetered
    }

    override fun dispose() {
        // Unregister the network callback when no longer needed
        // connectivityManager.unregisterNetworkCallback(networkCallback)
        context.unregisterReceiver(this)
    }

    private inline fun log(message: () -> String) {
        if (currentAniBuildConfig.isDebug) {
            logger.debug(message())
        }
    }
}

actual fun createMeteredNetworkDetector(context: Context): MeteredNetworkDetector {
    return AndroidMeteredNetworkDetector(context)
}