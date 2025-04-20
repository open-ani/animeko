/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.settings.FlowProxyProvider
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.domain.torrent.service.proxy.TorrentEngineProxy
import me.him188.ani.app.platform.createMeteredNetworkDetector
import me.him188.ani.app.torrent.anitorrent.AnitorrentDownloaderFactory
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.kiloBytes
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

sealed class AniTorrentService : LifecycleService() {
    private val scope = CoroutineScope(
        Dispatchers.Default + CoroutineName("AniTorrentService") +
                SupervisorJob(lifecycleScope.coroutineContext[Job]),
    )

    private val logger = logger<AniTorrentService>()

    // config flow for constructing torrent engine.
    private val saveDirDeferred: CompletableDeferred<String> = CompletableDeferred()
    private val proxyConfig: MutableSharedFlow<ProxyConfig?> = MutableSharedFlow(1)
    private val torrentPeerConfig: MutableSharedFlow<PeerFilterSettings> = MutableSharedFlow(1)
    private val anitorrentConfig: MutableSharedFlow<AnitorrentConfig> = MutableSharedFlow(1)

    // detect metered network state.
    private val meteredNetworkDetector by lazy { createMeteredNetworkDetector(this) }

    private val isClientBound: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val anitorrent: CompletableDeferred<AnitorrentEngine> = CompletableDeferred()

    private val binder by lazy {
        TorrentEngineProxy(
            saveDirDeferred,
            proxyConfig,
            torrentPeerConfig,
            anitorrentConfig,
            anitorrent,
            isClientBound,
            scope.coroutineContext,
        )
    }

    private val notification = ServiceNotification(this)
    private val alarmService: AlarmManager by lazy { getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    private val httpClientProvider = DefaultHttpClientProvider(FlowProxyProvider(proxyConfig), scope)
    private val httpClient = httpClientProvider.get()

    override fun onCreate() {
        super.onCreate()

        scope.launch {
            // try to initialize anitorrent engine.
            anitorrent.complete(
                AnitorrentEngine(
                    anitorrentConfig.combine(meteredNetworkDetector.isMeteredNetworkFlow) { config, isMetered ->
                        if (isMetered) config.copy(uploadRateLimit = 1.kiloBytes) else config
                    },
                    httpClient,
                    torrentPeerConfig,
                    Path(saveDirDeferred.await()).inSystem,
                    coroutineContext,
                    AnitorrentDownloaderFactory(),
                ),
            )
            logger.info { "anitorrent is initialized." }
        }

        scope.launch {
            val anitorrentDownloader = anitorrent.await().getDownloader()

            combine(
                anitorrentDownloader.openSessions.flatMapLatest { sessionMap ->
                    combine(sessionMap.values.map { it.sessionStats }) { sessions ->
                        sessions.count { it?.isDownloadFinished != true }
                    }
                },
                anitorrentDownloader.totalStats.sampleWithInitial(2000),
            ) { downloadingSessionCount, stats ->
                notification.updateNotification(
                    if (downloadingSessionCount > 0) {
                        NotificationDisplayStrategy.Working(
                            stats.downloadSpeed.bytes,
                            stats.uploadSpeed.bytes,
                            downloadingSessionCount,
                        )
                    } else {
                        NotificationDisplayStrategy.Idle(stats.downloadSpeed.bytes, stats.uploadSpeed.bytes)
                    },
                )
            }.collect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stopService", false) == true) {
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }

        notification.parseNotificationStrategyFromIntent(intent)
        val notificationResult = notification.createNotification(this)

        // 启动完成的广播
        sendBroadcast(
            Intent(INTENT_STARTUP).apply {
                setPackage(packageName)
                putExtra(INTENT_STARTUP_EXTRA, notificationResult)
            },
        )

        return if (notificationResult) START_STICKY else START_NOT_STICKY
    }


    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        logger.info { "client bind anitorrent." }
        isClientBound.value = true
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        logger.info { "client rebind anitorrent." }
        isClientBound.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        logger.info { "client unbind anitorrent." }
        isClientBound.value = false
        return true
    }

    /**
     * 在 app 被从最近任务界面划掉时重启服务
     *
     * 一些系统, 比如 MIUI, 会在划掉任务的时候杀死整个 app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServicePendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, this::class.java).apply {
                setPackage(packageName)
                putExtra("notification_appearance", notification.notificationAppearance)
            },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
        logger.info { "Task of Ani app is removed, scheduling restart service." }
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent,
        )

        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        // 发送后台执行超时广播
        sendBroadcast(Intent(INTENT_BACKGROUND_TIMEOUT).apply { setPackage(packageName) })
        stopSelf()
    }

    override fun onDestroy() {
        logger.info { "AniTorrentService is stopping." }
        meteredNetworkDetector.dispose()
        val engine = kotlin.runCatching { anitorrent.getCompleted() }.getOrNull() ?: return
        runBlocking(Dispatchers.IO_) {
            val downloader = engine.getDownloader()
            val sessions = downloader.openSessions.value

            withTimeoutOrNull(3000L) {
                sessions.forEach { (_, session) -> session.close() }
            }
            downloader.close()
        }
        // cancel background scope
        scope.cancel()
        super.onDestroy()
        // force kill process
        Process.killProcess(Process.myPid())
    }

    companion object {
        /**
         * 启动服务后的广播 Intent 动作, 通知启动结果. 服务必须在 `onStartCommand` 报告每一次启动结果.
         * Intent 必须传递 [INTENT_STARTUP_EXTRA] boolean 值作为启动结果, 如果没传 app 会认为启动失败.
         */
        const val INTENT_STARTUP = "me.him188.ani.android.ANI_TORRENT_SERVICE_STARTUP"
        const val INTENT_STARTUP_EXTRA = "success"

        const val INTENT_BACKGROUND_TIMEOUT = "me.him188.ani.android.ANI_TORRENT_SERVICE_BACKGROUND_TIMEOUT"

        val actualServiceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AniTorrentServiceApi34::class.java
        } else {
            AniTorrentServiceApiDefault::class.java
        }
    }
}

/**
 * Android 34 或以上使用
 *
 * 在 manifest 的 fgsType 是 mediaPlayback, 没被限制运行
 */
@RequiresApi(34)
class AniTorrentServiceApi34 : AniTorrentService()

/**
 * Android 34 以下使用
 *
 * 在 manifest 的 fgsType 是 dataSync
 */
class AniTorrentServiceApiDefault : AniTorrentService()