/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
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
import me.him188.ani.app.domain.torrent.service.proxy.TorrentDownloaderProxy
import me.him188.ani.utils.io.inSystem
import kotlin.coroutines.CoroutineContext

class AniTorrentService : LifecycleService(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + CoroutineName("AniTorrentService") + SupervisorJob()
    
    // config flow for constructing torrent engine.
    private val saveDirDeferred: CompletableDeferred<String> = CompletableDeferred()
    private val proxySettings: MutableSharedFlow<ProxySettings> = MutableSharedFlow(extraBufferCapacity = 1)
    private val torrentPeerConfig: MutableSharedFlow<TorrentPeerConfig> = MutableSharedFlow(extraBufferCapacity = 1)
    private val anitorrentConfig: MutableSharedFlow<AnitorrentConfig> = MutableSharedFlow(extraBufferCapacity = 1)
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val anitorrent: CompletableDeferred<AnitorrentEngine> = CompletableDeferred()

    private val binder = object : IRemoteAniTorrentEngine.Stub() {
        override fun getAnitorrentConfigFlow(): IAnitorrentConfigCallback {
            return object : IAnitorrentConfigCallback.Stub() {
                override fun onEmit(config: PAnitorrentConfig?) {
                    if (config != null) anitorrentConfig.tryEmit(
                        json.decodeFromString(AnitorrentConfig.serializer(), config.serializedJson)
                    )
                }
            }
        }

        override fun getProxySettingsFlow(): IProxySettingsCallback {
            return object : IProxySettingsCallback.Stub() {
                override fun onEmit(config: PProxySettings?) {
                    if (config != null) proxySettings.tryEmit(
                        json.decodeFromString(ProxySettings.serializer(), config.serializedJson)
                    )
                }
            }
        }

        override fun getTorrentPeerConfigFlow(): ITorrentPeerConfigCallback {
            return object : ITorrentPeerConfigCallback.Stub() {
                override fun onEmit(config: PTorrentPeerConfig?) {
                    if (config != null) torrentPeerConfig.tryEmit(
                        json.decodeFromString(TorrentPeerConfig.serializer(), config.serializedJson)
                    )
                }
            }
        }

        override fun setSaveDir(saveDir: String?) {
            if (saveDir != null) saveDirDeferred.complete(saveDir)
        }

        override fun getDownlaoder(): IRemoteTorrentDownloader {
            val downloader = runBlocking { anitorrent.await().getDownloader() }
            return TorrentDownloaderProxy(downloader, coroutineContext)
        }

    }
    
    init {
        launch {
            // try to initialize anitorrent engine.
            anitorrent.complete(
                AnitorrentEngine(
                    anitorrentConfig,
                    proxySettings,
                    torrentPeerConfig,
                    Path(saveDirDeferred.await()).inSystem,
                    coroutineContext
                )
            )
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
}