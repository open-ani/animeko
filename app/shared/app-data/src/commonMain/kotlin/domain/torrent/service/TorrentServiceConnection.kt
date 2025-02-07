/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Torrent 服务与 APP 通信接口. [T] 为通信接口的类型
 *
 * 此接口仅负责服务与 APP 之间的通信, 不负责服务的启动和终止.
 */
interface TorrentServiceConnection<T : Any> {
    /**
     * 当前服务是否已连接.
     *
     * 若变为 `false`, 则服务通信接口将变得不可用, 接口的实现类 可能需要重启服务, 例如 [LifecycleAwareTorrentServiceConnection].
     */
    val connected: StateFlow<Boolean>

    /**
     * 获取通信接口. 如果服务未连接, 则会挂起直到服务连接成功.
     *
     * 这个函数是线程安全的.
     */
    suspend fun getBinder(): T
}

/**
 * 通过 [Lifecycle] 来约束与服务的连接状态, 保证了:
 * - 在 [RESUMED][Lifecycle.State.RESUMED] 状态下, 根据[文档](https://developer.android.com/guide/components/activities/activity-lifecycle#onresume), APP 被视为在前台.
 * 服务未连接或终止, 则会立刻启动或重启服务保证可用性.
 * - 在 [CREATED][Lifecycle.State.CREATED] 和 [STARTED][Lifecycle.State.STARTED] 状态下,
 * 若服务终止, 不会立刻重启服务, 直到再次进入 [RESUMED][Lifecycle.State.RESUMED] 状态.
 *
 * 实现细节:
 *
 * @param starter 启动服务并返回[服务通信对象][T]接口, 若返回 null 代表启动失败.
 *   这个方法将在 `singleThreadDispatcher` 执行, 并且同时只有一个在执行.
 * @param singleThreadDispatcher 用于执行内部逻辑的调度器, 需要使用单线程来保证内部逻辑的线程安全.
 */
class LifecycleAwareTorrentServiceConnection<T : Any>(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    singleThreadDispatcher: CoroutineDispatcher,
    private val lifecycle: Lifecycle,
    private val starter: TorrentServiceStarter<T>,
) : TorrentServiceConnection<T> {
    private val logger = logger(this::class.simpleName ?: "TorrentServiceConnection")

    // we assert it is a single thread dispatcher
    private val scope = parentCoroutineContext.childScope(singleThreadDispatcher)
    
    private var binderDeferred by atomic(CompletableDeferred<T>())

    private val isAtForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isServiceConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val startServiceLock = Mutex()

    override val connected: StateFlow<Boolean> = isServiceConnected

    fun startLifecycleLoop() {
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                isAtForeground.value = true
                try {
                    if (!isServiceConnected.value) {
                        logger.debug { "Lifecycle resume: Service is not connected, start connecting..." }
                        startServiceWithRetry()
                    }

                    awaitCancellation()
                } catch (_: CancellationException) {
                    isAtForeground.value = false
                    logger.debug { "Lifecycle pause: App moved to background." }
                }
            }
        }
    }
    
    /**
     * 服务已断开连接, 通信对象变为不可用.
     * 如果目前 APP 还在前台, 就要尝试重启并重连服务.
     */
    fun onServiceDisconnected() {
        scope.launch(CoroutineName("TorrentServiceConnection - Lifecycle")) {
            if (!isServiceConnected.value) {
                // 已经是断开状态，直接忽略
                return@launch
            }
            isServiceConnected.value = false
            binderDeferred.cancel(CancellationException("Service disconnected."))
            binderDeferred = CompletableDeferred()

            // 若应用仍想要连接，则重新启动
            if (isAtForeground.value) {
                logger.debug { "Service is disconnected and app is in foreground, restarting service connection in 3s..." }
                startServiceWithRetry()
            } else {
                logger.debug { "Service is disconnected and app is in background." }
            }
        }
    }

    private suspend fun startServiceWithRetry(
        maxAttempts: Int = Int.MAX_VALUE, // 可根据需求设置
        delayMillisBetweenAttempts: Long = 2500
    ) {
        var attempt = 0
        while (attempt < maxAttempts && isAtForeground.value && !isServiceConnected.value) {
            val binder = try {
                startServiceLock.withLock {
                    if (!isAtForeground.value || isServiceConnected.value) {
                        logger.debug { "Service is already connected or app is not at foreground." }
                        return
                    }
                    starter.start()
                }
            } catch (ex: ServiceStartException) {
                logger.error(ex) { "[#$attempt] Failed to start service, retry after $delayMillisBetweenAttempts ms" }

                attempt++
                delay(delayMillisBetweenAttempts)

                continue
            }

            logger.debug { "Service connected successfully: $binder" }
            if (binderDeferred.isCompleted) {
                binderDeferred = CompletableDeferred()
            }
            binderDeferred.complete(binder)
            isServiceConnected.value = true
            return
        }
        if (!isServiceConnected.value) {
            logger.error { "Failed to connect service after $maxAttempts retries." }
        }
    }

    fun close() {
        scope.launch {
            logger.debug { "close(): Cancel scope, mark disconnected." }
            isServiceConnected.value = false
            binderDeferred.cancel(CancellationException("Connection closed."))
            scope.cancel()
        }
    }

    /**
     * 获取当前 binder 对象.
     *
     * - 如果服务还未连接, 此函数将挂起.
     */
    override suspend fun getBinder(): T {
        // track cancellation of [scope]
        return binderDeferred.await()
    }
}