/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.domain.torrent.IAnitorrentConfigCallback
import me.him188.ani.app.domain.torrent.IProxySettingsCallback
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.ITorrentPeerConfigCallback
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.parcel.PAnitorrentConfig
import me.him188.ani.app.domain.torrent.parcel.PProxySettings
import me.him188.ani.app.domain.torrent.parcel.PTorrentPeerConfig
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TorrentEngineProxy(
    private val saveDirDeferred: CompletableDeferred<String>,
    private val proxySettings: MutableSharedFlow<ProxySettings>,
    private val torrentPeerConfig: MutableSharedFlow<TorrentPeerConfig>,
    private val anitorrentConfig: MutableSharedFlow<AnitorrentConfig>,
    private val anitorrent: CompletableDeferred<AnitorrentEngine>,
    context: CoroutineContext,
) : IRemoteAniTorrentEngine.Stub() {
    private val logger = logger(this::class)
    private val scope = context.childScope()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getAnitorrentConfigFlow(): IAnitorrentConfigCallback {
        return object : IAnitorrentConfigCallback.Stub() {
            override fun onEmit(config: PAnitorrentConfig?) {
                logger.info { "received client AnitorrentConfig: $config" }
                if (config != null) anitorrentConfig.tryEmit(
                    json.decodeFromString(AnitorrentConfig.serializer(), config.serializedJson),
                )
            }
        }
    }

    override fun getProxySettingsFlow(): IProxySettingsCallback {
        return object : IProxySettingsCallback.Stub() {
            override fun onEmit(config: PProxySettings?) {
                logger.info { "received client ProxySettings: $config" }
                if (config != null) proxySettings.tryEmit(
                    json.decodeFromString(ProxySettings.serializer(), config.serializedJson),
                )
            }
        }
    }

    override fun getTorrentPeerConfigFlow(): ITorrentPeerConfigCallback {
        return object : ITorrentPeerConfigCallback.Stub() {
            override fun onEmit(config: PTorrentPeerConfig?) {
                logger.info { "received client TorrentPeerConfig: $config" }
                if (config != null) torrentPeerConfig.tryEmit(
                    json.decodeFromString(TorrentPeerConfig.serializer(), config.serializedJson),
                )
            }
        }
    }

    override fun setSaveDir(saveDir: String?) {
        logger.info { "received client saveDir: $saveDir" }
        if (saveDir != null) saveDirDeferred.complete(saveDir)
    }

    override fun getDownlaoder(): IRemoteTorrentDownloader {
        val downloader = runBlocking { anitorrent.await().getDownloader() }
        return TorrentDownloaderProxy(downloader, scope.coroutineContext)
    }
}