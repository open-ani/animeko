/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.app.domain.torrent.parcel.PAnitorrentConfig
import me.him188.ani.app.domain.torrent.parcel.PProxySettings
import me.him188.ani.app.domain.torrent.parcel.PTorrentPeerConfig
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.torrent.anitorrent.AnitorrentLibraryLoader
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteAnitorrentEngine(
    private val connection: TorrentServiceConnection,

    private val anitorrentConfigFlow: Flow<AnitorrentConfig>,
    private val proxySettingsFlow: Flow<ProxySettings>,
    private val peerFilterConfig: Flow<TorrentPeerConfig>,
    private val saveDir: SystemPath,
    parentCoroutineContext: CoroutineContext,
) : TorrentEngine {
    private val childScope = parentCoroutineContext.childScope()
    
    override val type: TorrentEngineType = TorrentEngineType.RemoteAnitorrent

    override val isSupported: Flow<Boolean> 
        get() = flowOf(true)

    override val location: MediaSourceLocation = MediaSourceLocation.Local
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    init {
        // transfer from app to service.
        childScope.launch {
            val configFlow = proxySettingsFlow.stateIn(this)
            connection.connected
                .filter { it }
                .map {
                    val proxySettingsCollector = getBinderOrFail().proxySettingsCollector
                    launch {
                        configFlow.collect {
                            val serialized = json.encodeToString(ProxySettings.serializer(), it)
                            proxySettingsCollector.collect(PProxySettings(serialized))
                        }
                    }
                }
                .onReplacement { it.cancel() }
                .collect()
            
        }
        childScope.launch {
            val configFlow = peerFilterConfig.stateIn(this)
            connection.connected
                .filter { it }
                .map {
                    val torrentPeerConfigCollector = getBinderOrFail().torrentPeerConfigCollector
                    launch {
                        configFlow.collect {
                            val serialized = json.encodeToString(TorrentPeerConfig.serializer(), it)
                            torrentPeerConfigCollector.collect(PTorrentPeerConfig(serialized))
                        }
                    }
                }
                .onReplacement { it.cancel() }
                .collect()

        }
        childScope.launch {
            val configFlow = anitorrentConfigFlow.stateIn(this)
            connection.connected
                .filter { it }
                .map {
                    val anitorrentConfigCollector = getBinderOrFail().anitorrentConfigCollector
                    launch {
                        configFlow.collect {
                            val serialized = json.encodeToString(AnitorrentConfig.serializer(), it)
                            anitorrentConfigCollector.collect(PAnitorrentConfig(serialized))
                        }
                    }
                }
                .onReplacement { it.cancel() }
                .collect()

        }
        childScope.launch {
            connection.connected
                .filter { it }
                .onEach { getBinderOrFail().setSaveDir(saveDir.absolutePath) }
                .collect()
        }
    }

    override suspend fun testConnection(): Boolean {
        return connection.connected.value
    }
    
    override suspend fun getDownloader(): TorrentDownloader {
        return RemoteTorrentDownloader {
            runBlocking { getBinderOrFail() }.downlaoder
        }
    }

    private suspend fun getBinderOrFail(): IRemoteAniTorrentEngine {
        return connection.awaitBinder()
    }

    override fun close() {
        childScope.cancel()
    }
}