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
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.UnsafeTorrentEngineAccessApi
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineType
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
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
 * @property useEngine StateFlow indicating if the engine is connected
 * @property lifecycle Manages the service connection lifecycle
 *
 * @see androidx.lifecycle.ProcessLifecycleOwner
 * @see ServiceConnection
 * @see AniTorrentService.onStartCommand
 * @see me.him188.ani.android.AniApplication
 */
class TorrentServiceConnectionManager(
    context: Context,
    private val dataStoreFlow: StateFlow<DataStore<List<MediaCacheSave>>>,
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
    override val useEngine: StateFlow<Boolean> = connection.connected

    @Volatile
    private var started = false

    fun launchCheckLoop() {
        if (started) return

        kotlinx.atomicfu.locks.synchronized(this) {
            if (started) return

            // 启动决定 service 是否启动的 lifecycle 的任务, 这个任务会决定 service 的生命周期.
            startObserveServiceLifecycle()
            // connection 会根据上面的 service 生命周期来决定合适真正连接 service.
            _connection.startLifecycleLoop()
            lifecycle.addObserver(serviceTimeLimitObserver)

            started = true
        }
    }

    @UnsafeTorrentEngineAccessApi
    override fun requestUseEngine(token: Any, use: Boolean): Boolean {
        logger.debug(Exception("show stacktrace")) {
            "Request ${if (use) "use" else "release"} torrent engine with token $token"
        }
        requestQueue.update {
            if (use) {
                add(token)
            } else {
                removeAll { it == token }
            }
        }
        return true
    }

    private fun startObserveServiceLifecycle() {
        scope.launch {
            combine(
                dataStoreFlow.flatMapLatest { it.data.map(::checkIfAllTorrentMediaCacheCompleted) },
                requestQueue.map { it.isNotEmpty() },
                useEngine,
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
                            shouldMoveToResumed -> Lifecycle.State.RESUMED
                            // 所有 BT 任务都完成了并且 requestUseEngine(false) 则不保持服务连接
                            else -> Lifecycle.State.STARTED
                        }

                        // 无论用户是否在前台, 只要服务还在连接并且不再需要 BT 服务了, 就一定发送关闭服务的任务
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
    private fun checkIfAllTorrentMediaCacheCompleted(list: List<MediaCacheSave>): Boolean {
        list.forEach { save ->
            if (save.engine != MediaCacheEngineKey(TorrentEngineType.Anitorrent.id)) {
                return@forEach
            }

            if (save.metadata.extra[TorrentMediaCacheEngine.EXTRA_TORRENT_COMPLETED] != "true") {
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