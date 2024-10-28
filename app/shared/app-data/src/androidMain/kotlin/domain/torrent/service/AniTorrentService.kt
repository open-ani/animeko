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
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.service.proxy.TorrentEngineProxy
import me.him188.ani.app.torrent.anitorrent.AnitorrentDownloaderFactory
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class AniTorrentService : LifecycleService(), CoroutineScope {
    private val logger = logger(this::class)
    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + CoroutineName("AniTorrentService") + SupervisorJob()
    
    // config flow for constructing torrent engine.
    private val saveDirDeferred: CompletableDeferred<String> = CompletableDeferred()
    private val proxySettings: MutableSharedFlow<ProxySettings> = MutableSharedFlow(1)
    private val torrentPeerConfig: MutableSharedFlow<TorrentPeerConfig> = MutableSharedFlow(1)
    private val anitorrentConfig: MutableSharedFlow<AnitorrentConfig> = MutableSharedFlow(1)
    
    private val anitorrent: CompletableDeferred<AnitorrentEngine> = CompletableDeferred()

    private val binder by lazy {
        TorrentEngineProxy(
            saveDirDeferred,
            proxySettings,
            torrentPeerConfig,
            anitorrentConfig,
            anitorrent,
            coroutineContext,
        )
    }

    private val notification = ServiceNotification(this)
    
    override fun onCreate() {
        super.onCreate()
        
        launch {
            // try to initialize anitorrent engine.
            anitorrent.complete(
                AnitorrentEngine(
                    anitorrentConfig,
                    proxySettings,
                    torrentPeerConfig,
                    Path(saveDirDeferred.await()).inSystem,
                    coroutineContext,
                    AnitorrentDownloaderFactory()
                )
            )
            logger.info { "anitorrent is initialized." }
        }

        launch {
            val anitorrentDownloader = anitorrent.await().getDownloader()
            anitorrentDownloader.totalStats.sampleWithInitial(1000).collect { stat ->
                notification.updateNotification(
                    NotificationDisplayStrategy.Idle(stat.downloadSpeed.bytes, stat.uploadSpeed.bytes),
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stopService", false) == true) {
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }

        notification.parseNotificationStrategyFromIntent(intent)
        notification.createNotification(this)
        return START_STICKY
    }
    
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        logger.info { "client bind anitorrent." }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        logger.info { "client unbind anitorrent." }
        return true
    }
}