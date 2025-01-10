/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.client.DefaultConnectivityAware
import me.him188.ani.app.domain.torrent.collector.IAnitorrentConfigCollector
import me.him188.ani.app.domain.torrent.collector.IProxySettingsCollector
import me.him188.ani.app.domain.torrent.collector.ITorrentPeerConfigCollector
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.parcel.PAnitorrentConfig
import me.him188.ani.app.domain.torrent.parcel.PProxyConfig
import me.him188.ani.app.domain.torrent.parcel.PTorrentPeerFilterSettings
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TorrentEngineProxy(
    private val saveDirDeferred: CompletableDeferred<String>,
    private val proxyConfig: MutableSharedFlow<ProxyConfig?>,
    private val peerFilterSettings: MutableSharedFlow<PeerFilterSettings>,
    private val anitorrentConfig: MutableSharedFlow<AnitorrentConfig>,
    private val anitorrent: CompletableDeferred<AnitorrentEngine>,
    isClientBound: StateFlow<Boolean>,
    context: CoroutineContext,
) : IRemoteAniTorrentEngine.Stub() {
    private val logger = logger<TorrentEngineProxy>()
    private val scope = context.childScope()
    private val connectivityAware = DefaultConnectivityAware(context.childScope(), isClientBound)

    // cache downloader in case clients always get the same downloader proxy instance.
    private var currentDownloader: TorrentDownloader? = null

    private val downloaderProxy = flow<TorrentDownloader> {
        val newDownloader = anitorrent.await().getDownloader()
        if (currentDownloader == null || newDownloader !== currentDownloader) {
            emit(newDownloader)
            currentDownloader = newDownloader
        }
    }
        .distinctUntilChanged()
        .map { TorrentDownloaderProxy(it, connectivityAware, scope.coroutineContext) }
        .stateIn(scope, SharingStarted.Lazily, null)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getAnitorrentConfigCollector(): IAnitorrentConfigCollector {
        return object : IAnitorrentConfigCollector.Stub() {
            override fun collect(config: PAnitorrentConfig?) {
                logger.info { "received client AnitorrentConfig: $config" }
                if (config != null) anitorrentConfig.tryEmit(
                    json.decodeFromString(AnitorrentConfig.serializer(), config.serializedJson),
                )
            }
        }
    }

    override fun getProxySettingsCollector(): IProxySettingsCollector {
        return object : IProxySettingsCollector.Stub() {
            override fun collect(config: PProxyConfig?) {
                logger.info { "received client ProxyConfig: $config" }
                if (config != null) proxyConfig.tryEmit(
                    json.decodeFromString(ProxyConfig.serializer().nullable, config.serializedJson),
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun getTorrentPeerConfigCollector(): ITorrentPeerConfigCollector {
        return object : ITorrentPeerConfigCollector.Stub() {
            override fun collect(config: PTorrentPeerFilterSettings?) {
                logger.info { "received client TorrentPeerConfig: $config" }
                if (config != null) peerFilterSettings.tryEmit(config.toPeerFilterSettings())
            }
        }
    }

    override fun setSaveDir(saveDir: String?) {
        logger.info { "received client saveDir: $saveDir" }
        if (saveDir != null) saveDirDeferred.complete(saveDir)
    }

    override fun getDownlaoder(): IRemoteTorrentDownloader {
        return runBlocking { downloaderProxy.filterNotNull().first() }
    }
}