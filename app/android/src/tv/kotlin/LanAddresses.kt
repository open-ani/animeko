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
    // 各网络的局域网地址, 由回调携带的链路信息维护.
    val addresses = LinkedHashMap<Network, String?>()
    fun sendCurrent() {
        trySend(addresses.values.firstOrNull { it != null })
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            addresses[network] = properties.linkAddresses.firstNotNullOfOrNull {
                (it.address as? Inet4Address)?.hostAddress?.takeIf(RemoteSettingsLink::isPrivateIpv4)
            }
            sendCurrent()
        }

        override fun onLost(network: Network) {
            addresses.remove(network)
            sendCurrent()
        }
    }
    // 局域网不一定能访问互联网. 请求默认只匹配非 VPN 网络.
    val request = NetworkRequest.Builder()
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()
    // 没有匹配的网络时不会有回调.
    sendCurrent()
    manager.registerNetworkCallback(request, callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.conflate().distinctUntilChanged()
