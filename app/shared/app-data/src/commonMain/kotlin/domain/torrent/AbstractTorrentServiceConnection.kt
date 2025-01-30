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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 管理与服务的连接. [T] 代表与服务进行通信的c.
 *
 * 这个类通过 [Lifecycle] 来约束与服务的连接状态, 保证了:
 * - 在 [RESUMED][Lifecycle.State.RESUMED] 状态下, 根据[文档](https://developer.android.com/guide/components/activities/activity-lifecycle#onresume), APP 被视为在前台.
 * 服务未连接或终止, 则会立刻启动或重启服务保证可用性.
 * - 在 [CREATED][Lifecycle.State.CREATED] 和 [STARTED][Lifecycle.State.STARTED] 状态下,
 * 若服务终止, 不会立刻重启服务, 直到再次进入 [RESUMED][Lifecycle.State.RESUMED] 状态.
 *
 * 实现细节:
 * - 实现 [startService] 方法, 用于实际的启动服务.
 * - 服务启动完成后，通过具体实现的监听方式调用 [onServiceConnected] 或 [onServiceDisconnected] 方法.
 */
abstract class AbstractTorrentServiceConnection<T : Any>(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : DefaultLifecycleObserver {
    protected val logger = logger(this::class.simpleName ?: "TorrentServiceConnection")
    private val scope = coroutineContext.childScope()

    private val lock = Mutex()
    private var binderDeferred by atomic(CompletableDeferred<T>())

    private val isAtForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isServiceConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val connected: StateFlow<Boolean> = isServiceConnected

    /**
     * 启动服务并返回启动结果.
     *
     * 注意：此处同步返回的结果仅代表服务是否成功启动, 不代表服务是否已连接.
     * 换句话说, 此方法返回 [StartResult.STARTED] 或 [StartResult.ALREADY_RUNNING] 后,
     * 实现类必须尽快调用 [onServiceConnected] 并传入服务通信接口对象.
     *
     *
     * @return `true` if the service is started successfully, `false` otherwise.
     */
    abstract suspend fun startService(): StartResult

    /**
     * 服务已连接, 服务通信对象一定可用.
     * 无论当前 [Lifecycle] 什么状态都要应用新的 [binder].
     */
    protected fun onServiceConnected(binder: T) {
        scope.launch(CoroutineName("TorrentServiceConnection - On Service Connected")) {
            lock.withLock {
                logger.info { "Service is connected, got binder $binder" }
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
            lock.withLock {
                isServiceConnected.value = false

                binderDeferred.cancel(CancellationException("Service disconnected."))
                binderDeferred = CompletableDeferred()

                if (isAtForeground.value) {
                    logger.info { "Service is disconnected while app is at foreground, restarting." }
                    val startResult = startService()
                    if (startResult == StartResult.FAILED) {
                        logger.warn { "Failed to start service, all binder getter will suspended." }
                    }
                }
            }
        }
    }

    /**
     * 获取当前 binder 对象.
     * 如果服务未连接, 则会挂起直到服务连接成功.
     */
    suspend fun getBinder(): T {
        isServiceConnected.first { it }
        return binderDeferred.await()
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

                logger.info { "Service is not started, starting." }
                val startResult = startService()
                if (startResult == StartResult.FAILED) {
                    logger.warn { "Failed to start service, all binder getter will suspended." }
                }
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