/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import androidx.annotation.CallSuper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * torrent 服务与 APP 通信接口.
 */
interface TorrentServiceConnection<T : Any> {
    /**
     * 当前服务是否已连接, 只有在已连接的状态才能获取通信接口.
     *
     * 若变为 `false`, 则服务通信接口将变得不可用, 调用任何通信接口的方法将会导致不可预测的结果.
     * 此时需要再次调用 [startService] 来重新启动服务.
     */
    val connected: StateFlow<Boolean>

    /**
     * 启动服务. 调用此方法后, 服务将会启动并尽快连接.
     *
     * 若返回了 [StartResult.STARTED] 或 [StartResult.FAILED],
     * [connected] 将在未来变为 `true`, 届时 [getBinder] 将会立刻返回服务通信接口.
     */
    suspend fun startService(): StartResult

    /**
     * 获取通信接口. 如果服务未连接, 则会挂起直到服务连接成功.
     */
    suspend fun getBinder(): T

    /**
     * Start result of [startService]
     */
    enum class StartResult {
        /**
         * Service is started, binder should be later retrieved by [onServiceConnected]
         */
        STARTED,

        /**
         * Service is already running
         */
        ALREADY_RUNNING,

        /**
         * Service started failed.
         */
        FAILED
    }
}

/**
 * 通过 [Lifecycle] 来约束与服务的连接状态, 保证了:
 * - 在 [RESUMED][Lifecycle.State.RESUMED] 状态下, 根据[文档](https://developer.android.com/guide/components/activities/activity-lifecycle#onresume), APP 被视为在前台.
 * 服务未连接或终止, 则会立刻启动或重启服务保证可用性.
 * - 在 [CREATED][Lifecycle.State.CREATED] 和 [STARTED][Lifecycle.State.STARTED] 状态下,
 * 若服务终止, 不会立刻重启服务, 直到再次进入 [RESUMED][Lifecycle.State.RESUMED] 状态.
 *
 * 实现细节:
 * - 实现 [startService] 方法, 用于实际的启动服务, 并且要连接服务.
 * - 服务连接完成，调用 [onServiceConnected] 传入服务通信接口对象.
 * - 如果服务断开连接了, 调用 [onServiceDisconnected], 会自动判断是否需要重连.
 */
abstract class LifecycleAwareTorrentServiceConnection<T : Any>(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : DefaultLifecycleObserver, TorrentServiceConnection<T> {
    protected val logger = logger(this::class.simpleName ?: "TorrentServiceConnection")

    @OptIn(DelicateCoroutinesApi::class)
    private val dispatcher = newFixedThreadPoolContext(2, "LifecycleAwareTorrentServiceConnection")
    private val scope = parentCoroutineContext.childScope(dispatcher)

    private val lock = Mutex()
    private var binderDeferred by atomic(CompletableDeferred<T>())

    private val isAtForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isServiceConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val connected: StateFlow<Boolean> = isServiceConnected

    /**
     * 启动服务并返回启动结果.
     *
     * 注意：此处同步返回的结果仅代表服务是否成功启动, 不代表服务是否已连接.
     * 换句话说, 此方法返回 [StartResult.STARTED][TorrentServiceConnection.StartResult.STARTED] 或 [StartResult.ALREADY_RUNNING][TorrentServiceConnection.StartResult.ALREADY_RUNNING] 后,
     * 实现类必须尽快调用 [onServiceConnected] 并传入服务通信接口对象.
     *
     *
     * @return `true` if the service is started successfully, `false` otherwise.
     */
    abstract override suspend fun startService(): TorrentServiceConnection.StartResult

    /**
     * 服务已连接, 服务通信对象一定可用.
     * 无论当前 [Lifecycle] 什么状态都要应用新的 [binder].
     */
    protected fun onServiceConnected(binder: T) {
        scope.launch(CoroutineName("TorrentServiceConnection - On Service Connected")) {
            lock.withLock {
                logger.debug { "Service is connected, got binder $binder" }
                if (binderDeferred.isCompleted) {
                    binderDeferred = CompletableDeferred(binder)
                } else {
                    binderDeferred.complete(binder)
                }
                isServiceConnected.value = true
            }
        }
    }

    /**
     * 服务已断开连接, 通信对象变为不可用.
     * 如果目前 APP 还在前台, 就要尝试重启并重连服务.
     */
    protected fun onServiceDisconnected() {
        scope.launch(CoroutineName("TorrentServiceConnection - On Service Disconnected")) {
            if (!isServiceConnected.value) return@launch
            lock.withLock {
                if (!isServiceConnected.value) return@launch
                isServiceConnected.value = false

                binderDeferred.cancel(CancellationException("Service disconnected."))
                binderDeferred = CompletableDeferred()

                if (isAtForeground.value) {
                    logger.debug { "Service is disconnected while app is at foreground, restarting." }
                    startServiceWithRetry()
                }
            }
        }
    }

    /**
     * APP 已进入前台, 此时需要保证服务可用.
     */
    @CallSuper
    override fun onResume(owner: LifecycleOwner) {
        scope.launch(CoroutineName("TorrentServiceConnection - Lifecycle Resume")) {
            isAtForeground.value = true
            // 服务已经连接了, 不需要再次处理
            if (isServiceConnected.value) return@launch

            lock.withLock {
                if (isServiceConnected.value) return@launch

                logger.debug { "Service is not started, starting." }
                startServiceWithRetry()
            }
        }
    }

    @CallSuper
    override fun onPause(owner: LifecycleOwner) {
        scope.launch(CoroutineName("TorrentServiceConnection - Lifecycle Pause")) {
            lock.withLock {
                isAtForeground.value = false
            }
        }
    }

    private suspend fun startServiceWithRetry(maxAttempts: Int = Int.MAX_VALUE) {
        var retries = 0
        while (retries <= maxAttempts) {
            val startResult = startService()
            if (startResult == TorrentServiceConnection.StartResult.FAILED) {
                logger.warn { "[#$retries] Failed to start service." }
                retries++
                delay(7500)
            } else {
                return
            }
        }
        logger.error { "Failed to start service after $maxAttempts retries." }
    }

    fun close() {
        scope.cancel()
        dispatcher.close()
        isServiceConnected.value = false
        binderDeferred.cancel(CancellationException("TorrentServiceConnection closed."))
    }

    /**
     * 获取当前 binder 对象.
     * 如果服务未连接, 则会挂起直到服务连接成功; 如果连接已[关闭][close], 则直接抛出 [CancellationException].
     */
    override suspend fun getBinder(): T {
        // track cancellation of [scope]
        scope.async {
            isServiceConnected.first { it }
        }.await()
        return binderDeferred.await()
    }
}