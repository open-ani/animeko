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
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.RandomAccessFile
import java.net.Inet4Address
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHost
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostStatus
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.tv.remotesettings.HmacSettingsRevision
import me.him188.ani.tv.remotesettings.LocalRemoteSettingsBackend
import me.him188.ani.tv.remotesettings.RemoteSettingsServer
import org.koin.core.Koin

/** Application-scoped host; foreground activities only own runtime permission prompts. */
class AndroidRemoteSettingsHost(context: Context, koin: Koin, scope: CoroutineScope) :
    RemoteSettingsHost {
    private val application = context.applicationContext
    private val mutableState = MutableStateFlow(RemoteSettingsHostState())
    override val state = mutableState.asStateFlow()
    private val stores = application.dataStores
    private val version = currentAniBuildConfig.versionName
    private val server =
        RemoteSettingsServer(
            scope,
            LocalRemoteSettingsBackend(
                koin.get(),
                stores,
                koin.get(),
                koin.get(),
                koin.get(),
                HmacSettingsRevision(),
            ) {
                val file = application.filesDir.resolve("logs/app.log")
                if (!file.isFile) LogSnapshot("tv-app.log", "", false)
                else
                    RandomAccessFile(file, "r").use { input ->
                        val count =
                            minOf(input.length(), RemoteSettingsProtocol.MAX_LOG_BYTES.toLong())
                                .toInt()
                        input.seek(input.length() - count)
                        val bytes = ByteArray(count)
                        input.readFully(bytes)
                        LogSnapshot("tv-app.log", bytes.decodeToString(), input.length() > count)
                    }
            },
            version,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            { stores.selfInfoStore.data.first()?.id?.toString() },
        )

    init {
        scope.launch(Dispatchers.IO) {
            var port: Int? = null
            try {
                while (isActive) {
                    try {
                        val allowed =
                            Build.VERSION.SDK_INT < 37 ||
                                application.checkSelfPermission(LOCAL_NETWORK_PERMISSION) ==
                                    PackageManager.PERMISSION_GRANTED
                        if (!allowed)
                            mutableState.value =
                                RemoteSettingsHostState(status = RemoteSettingsHostStatus.PERMISSION_REQUIRED)
                        else {
                            if (port == null) port = server.start()
                            val address = lanAddress()
                            mutableState.value =
                                if (address == null)
                                    RemoteSettingsHostState(status = RemoteSettingsHostStatus.NO_NETWORK)
                                else
                                    RemoteSettingsHostState(
                                        RemoteSettingsLink(
                                            address,
                                            port,
                                            server.accessKey,
                                            version,
                                            stores.selfInfoStore.data
                                                .first()
                                                ?.id
                                                ?.toString()
                                                .orEmpty(),
                                        ),
                                        RemoteSettingsHostStatus.READY,
                                    )
                        }
                        delay(3_000)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        mutableState.value = RemoteSettingsHostState(status = RemoteSettingsHostStatus.UNAVAILABLE)
                        delay(10_000)
                    }
                }
            } finally {
                server.close()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun lanAddress(): String? {
        val manager =
            application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities =
                manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            )
                return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull {
                (it.address as? Inet4Address)
                    ?.hostAddress
                    ?.takeIf(RemoteSettingsLink::isPrivateIpv4)
            }
        }
    }

    companion object {
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
