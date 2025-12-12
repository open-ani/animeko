/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoEntity
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.UnsafeTorrentEngineAccessApi
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Manages the lifecycle and connection to the [AniTorrentService] for torrent operations.
 *
 * This class coordinates the torrent service lifecycle based on application state and download completion.
 * It implements [TorrentEngineAccess] to provide controlled access to the torrent engine and monitors
 * the service connection status.
 *
 * Key features:
 * - Manages service connection based on app foreground/background state
 * - Keeps service alive when downloads are in progress
 * - Automatically stops service when all downloads are complete
 * - Observes Android's background service time limits (particularly for Android 15)
 *
 * @property connection Provides access to the remote torrent engine
 * @property isServiceConnected StateFlow indicating if the engine is connected
 * @property lifecycle Manages the service connection lifecycle
 *
 * @see androidx.lifecycle.ProcessLifecycleOwner
 * @see ServiceConnection
 * @see AniTorrentService.onStartCommand
 * @see me.him188.ani.android.AniApplication
 */
class TorrentServiceConnectionManager(
    context: Context,
    private val torrentCacheInfoDao: StateFlow<TorrentCacheInfoDao?>,
    private val mediaCacheBaseSaveDirFlow: StateFlow<File?>,
    startServiceImpl: () -> ComponentName?,
    private val stopServiceImpl: () -> Unit,
    private val processLifecycle: Lifecycle,
    parentCoroutineContext: CoroutineContext,
) : LifecycleOwner, TorrentEngineAccess, SynchronizedObject() {
    private companion object {
        private const val MSG_STOP_SERVICE = 1
    }

    private val logger = logger<TorrentServiceConnectionManager>()
    private val scope = parentCoroutineContext.childScope()
    private val serviceLifecycleRegistry = LifecycleRegistry(this)
    private val stopServiceHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_STOP_SERVICE) {
            if (serviceLifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
                stopServiceImpl()
            }
        }
        true
    }

    private val serviceStarter = AniTorrentServiceStarter(
        context = context,
        startServiceImpl = startServiceImpl,
        onServiceDisconnected = ::onServiceDisconnected,
    )

    // TorrentServiceConnection 无论如何都不能被阻塞, 单独为它的逻辑创建一个线程.
    @OptIn(DelicateCoroutinesApi::class)
    private val _connection = LifecycleAwareTorrentServiceConnection(
        parentCoroutineContext = parentCoroutineContext +
                newSingleThreadContext("AndroidTorrentServiceConnection"),
        lifecycle = serviceLifecycleRegistry,
        serviceStarter,
    )

    private val serviceTimeLimitObserver = ForegroundServiceTimeLimitObserver(context) {
        logger.warn { "Service background time exceeded." }
        _connection.onServiceDisconnected()
    }

    override val lifecycle: Lifecycle = serviceLifecycleRegistry
    private val requestQueue = MutableStateFlow(persistentListOf<Any>())

    val connection: TorrentServiceConnection<IRemoteAniTorrentEngine> get() = _connection
    override val isServiceConnected: StateFlow<Boolean> = connection.connected

    @Volatile
    private var started = false

    fun launchCheckLoop() {
        if (started) return

        kotlinx.atomicfu.locks.synchronized(this) {
            if (started) return

            startObserveServiceLifecycle()
            _connection.startLifecycleLoop()
            lifecycle.addObserver(serviceTimeLimitObserver)

            started = true
        }
    }

    @UnsafeTorrentEngineAccessApi
    override fun requestService(token: Any, use: Boolean): Boolean {
        requestQueue.update {
            if (use) {
                add(token)
            } else {
                removeAll { it == token }
            }
        }
        return true
    }

    /**
     * 启动监控服务生命周期的协程。
     * 通过组合多个数据流来决定服务的运行状态：
     * 1. 所有 Torrent 下载任务是否完成
     * 2. 是否有显式的请求保持服务运行（通过 requestQueue）
     * 3. 服务当前是否已连接
     * 4. 应用程序当前的生命周期状态
     *
     * 根据这些因素决定是否将服务生命周期保持在 RESUMED 状态 (保持连接) 或 STARTED 状态 (允许断开连接).
     */
    private fun startObserveServiceLifecycle() {
        scope.launch {
            combine(
                torrentCacheInfoDao.flatMapLatest {
                    it?.getAll()?.map(::allTorrentMediaCacheCompleted) ?: emptyFlow()
                },
                requestQueue.map { it.isNotEmpty() },
                isServiceConnected,
                processLifecycle.currentStateFlow,
            ) { allCompleted, keep, connected, currentState ->
                Triple(currentState, keep || !allCompleted, connected)
            }
                .distinctUntilChanged()
                .map { (currentState, shouldMoveToResumed, connected) ->
                    withContext(Dispatchers.Main) {
                        serviceLifecycleRegistry.currentState = when {
                            // 应用不在前台时始终不保持服务连接, 用户可以随时点通知消息的停止来停止服务
                            currentState != Lifecycle.State.RESUMED -> currentState
                            // 应用在前台并且需要服务时 (requestUseEngine(true) 或有未完成的 BT 任务), 必须保持服务的连接
                            shouldMoveToResumed -> Lifecycle.State.RESUMED // 随后 LifecycleAwareTorrentServiceConnection 会监听到状态并启动 service
                            // 所有 BT 任务都完成了并且 requestUseEngine(false) 则不保持服务连接
                            else -> Lifecycle.State.STARTED
                        }

                        // 手动停止不需要的 service, 因为 LifecycleAwareTorrentServiceConnection 只会启动 service, 不会停止.
                        if (!shouldMoveToResumed && connected) {
                            val message = Message.obtain(stopServiceHandler, MSG_STOP_SERVICE)
                            stopServiceHandler.sendMessageDelayed(message, 5000)
                        } else {
                            // 如果还需要服务, 那就移除关闭服务的任务
                            stopServiceHandler.removeMessages(MSG_STOP_SERVICE)
                        }
                    }
                    logger.info { "Use torrent engine: $shouldMoveToResumed, connected: $connected" }
                }
                .collect()
        }
    }

    /**
     * Check if all torrent media cache is completed. If not, the service will be kept alive.
     */
    private fun allTorrentMediaCacheCompleted(list: List<TorrentCacheInfoEntity>): Boolean {
        val baseSaveDir = mediaCacheBaseSaveDirFlow.value ?: return true
        list.forEach { entity ->
            if (!entity.completed) return false
            val pathInTorrent = entity.pathInTorrent.takeIf { it.isNotEmpty() } ?: return false

            val file = File(baseSaveDir, entity.relativeDir).resolve(pathInTorrent)
            if (!file.exists() || file.isDirectory()) {
                return false
            }
        }

        return true
    }

    private fun onServiceDisconnected() {
        _connection.onServiceDisconnected()
    }
}

/**
 * According to [documentation](https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout):
 * in Android 15, foreground service with type `dataSync` or `mediaProcessing` is limited to run in background for 6 hours.
 *
 * This observer will listen to the time limit exceeded broadcast and update service connection state of app.
 */
private class ForegroundServiceTimeLimitObserver(
    private val context: Context,
    onServiceTimeLimitExceeded: () -> Unit
) : DefaultLifecycleObserver {
    private var registered = false
    private val timeExceedLimitIntentFilter = IntentFilter(AniTorrentService.INTENT_BACKGROUND_TIMEOUT)
    private val timeExceedLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onServiceTimeLimitExceeded()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)

        // app 到后台的时候注册监听
        if (!registered) {
            ContextCompat.registerReceiver(
                context,
                timeExceedLimitReceiver,
                timeExceedLimitIntentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        // app 到前台的时候取消监听
        if (registered) {
            context.unregisterReceiver(timeExceedLimitReceiver)
            registered = false
        }
    }
}