/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
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
import me.him188.ani.app.torrent.anitorrent.AnitorrentDownloaderFactory
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
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val anitorrent: CompletableDeferred<AnitorrentEngine> = CompletableDeferred()

    private val binder = object : IRemoteAniTorrentEngine.Stub() {
        override fun getAnitorrentConfigFlow(): IAnitorrentConfigCallback {
            return object : IAnitorrentConfigCallback.Stub() {
                override fun onEmit(config: PAnitorrentConfig?) {
                    logger.info { "received client AnitorrentConfig: $config" }
                    if (config != null) anitorrentConfig.tryEmit(
                        json.decodeFromString(AnitorrentConfig.serializer(), config.serializedJson)
                    )
                }
            }
        }

        override fun getProxySettingsFlow(): IProxySettingsCallback {
            return object : IProxySettingsCallback.Stub() {
                override fun onEmit(config: PProxySettings?) {
                    logger.info { "received client ProxySettings: $config" }
                    if (config != null) proxySettings.tryEmit(
                        json.decodeFromString(ProxySettings.serializer(), config.serializedJson)
                    )
                }
            }
        }

        override fun getTorrentPeerConfigFlow(): ITorrentPeerConfigCallback {
            return object : ITorrentPeerConfigCallback.Stub() {
                override fun onEmit(config: PTorrentPeerConfig?) {
                    logger.info { "received client TorrentPeerConfig: $config" }
                    if (config != null) torrentPeerConfig.tryEmit(
                        json.decodeFromString(TorrentPeerConfig.serializer(), config.serializedJson)
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
            return TorrentDownloaderProxy(downloader, coroutineContext)
        }

    }
    
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stopService", false) == true) {
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }

        pushNotification(intent)
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

    private fun pushNotification(startIntent: Intent?) {
        val notificationName = (startIntent?.getIntExtra("app_name", -1) ?: -1)
            .let { if (it == -1) "Animeko" else getString(it) }
        val notificationContentText = (startIntent?.getIntExtra("app_service_content_text", -1) ?: -1)
            .let { if (it == -1) "Animeko BT 引擎正在运行中" else getString(it) }
        val notificationStickerContent = (startIntent?.getIntExtra("app_service_content_text", -1) ?: -1)
            .let { if (it == -1) "Animeko BT 引擎正在运行中" else getString(it) }
        val notificationIcon = (startIntent?.getIntExtra("app_icon", -1) ?: -1)
            .let { if (it != -1) defaultNotificationIcon else Icon.createWithResource(this, it) }

        val openActivityIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startIntent?.getParcelableExtra("open_activity_intent", Intent::class.java)
        } else {
            startIntent?.getParcelableExtra<Intent>("open_activity_intent")
        }
        
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existingNotification = notificationManager.activeNotifications.find { it.id == NOTIFICATION_ID }

        if (existingNotification == null) {
            val channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
                ?: NotificationChannel(NOTIFICATION_CHANNEL_ID, notificationName, NotificationManager.IMPORTANCE_HIGH)
                    .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
                    .also { notificationManager.createNotificationChannel(it) }

            val openActivityAction = if (openActivityIntent == null) null else
                PendingIntent.getActivity(this, 0, openActivityIntent, PendingIntent.FLAG_IMMUTABLE)
            val stopServiceAction = PendingIntent.getService(
                this, 0,
                Intent(this, this::class.java).apply { putExtra("stopService", true) },
                PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = Notification.Builder(this, channel.id).apply {
                setContentTitle(notificationName)
                setContentText(notificationContentText)
                setSmallIcon(notificationIcon)
                setContentIntent(openActivityAction)
                setActions(
                    Notification.Action.Builder(notificationIcon, "停止", stopServiceAction).build(),
                )
                setTicker(notificationStickerContent)
                setVisibility(Notification.VISIBILITY_PUBLIC)
            }.build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    companion object {
        private const val NOTIFICATION_ID = 114
        private const val NOTIFICATION_CHANNEL_ID = "me.him188.ani.app.domain.torrent.service.AniTorrentService"
        private val defaultNotificationIcon: Icon by lazy {
            Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }
}