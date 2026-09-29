/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.remote.settings.RemoteSettingsLink

/**
 * 手机可以连接的电视局域网 IPv4 地址, 随网络变化更新. 没有 Wi-Fi 或以太网 (或只有 VPN) 时为 `null`.
 */
internal fun lanAddresses(context: Context): Flow<String?> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(manager.lanAddress())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(manager.lanAddress())
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            trySend(manager.lanAddress())
        }

        override fun onLost(network: Network) {
            // 回调时该网络可能仍在 allNetworks 中.
            trySend(manager.lanAddress(excluding = network))
        }
    }
    // 局域网不一定能访问互联网.
    val request = NetworkRequest.Builder()
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()
    trySend(manager.lanAddress())
    manager.registerNetworkCallback(request, callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.conflate().distinctUntilChanged()

@Suppress("DEPRECATION")
private fun ConnectivityManager.lanAddress(excluding: Network? = null): String? =
    allNetworks.firstNotNullOfOrNull { network ->
        if (network == excluding) return@firstNotNullOfOrNull null
        val capabilities = getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
        val local = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
        getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull {
            (it.address as? Inet4Address)?.hostAddress?.takeIf(RemoteSettingsLink::isPrivateIpv4)
        }
    }
