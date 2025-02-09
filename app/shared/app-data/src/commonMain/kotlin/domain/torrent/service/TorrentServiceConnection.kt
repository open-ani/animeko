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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Torrent 服务与 APP 通信接口. [T] 为通信接口的类型
 *
 * 此接口仅负责与 Torrent 服务的通信, 启动与终止服务的逻辑可能需要在 接口的实现类(implementations) 或其他外部实现.
 */
interface TorrentServiceConnection<T : Any> {
    /**
     * 当前服务是否已连接.
     *
     * 若变为 `false`, 则服务通信接口将变得不可用, 接口的实现类(implementations) 可能需要重启服务, 例如 [LifecycleAwareTorrentServiceConnection].
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
 * @param parentCoroutineContext 执行内部逻辑的协程上下文.
 */
class LifecycleAwareTorrentServiceConnection<T : Any>(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val lifecycle: Lifecycle,
    private val starter: TorrentServiceStarter<T>,
) : TorrentServiceConnection<T> {
    private val logger = logger(this::class.simpleName ?: "LifecycleAwareTorrentServiceConnection")

    private val scope = parentCoroutineContext.childScope()

    private val binderDeferred = MutableStateFlow(CompletableDeferred<T>())

    // read at `onServiceDisconnected`, set by `startLifecycleLoop`
    private val isAtForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isServiceConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val lock = Mutex()
    private val startServiceFlow = MutableStateFlow(Any())

    private val lifecycleLoopLock = SynchronizedObject()
    private var started = false

    override val connected: StateFlow<Boolean> = isServiceConnected

    fun startLifecycleLoop() {
        if (started) return

        synchronized(lifecycleLoopLock) {
            if (started) return

            scope.launch(CoroutineName("TorrentServiceConnection - RepeatOnResume")) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    isAtForeground.value = true
                    try {
                        startServiceFlow.collectLatest {
                            // 如果 collectLatest 执行后 onServiceDisconnected 也立刻 launch, 最终状态应该是断开了连接
                            // 此处让出调度权, 以便 onServiceDisconnected 可以立刻执行
                            // 让出调度后, onServiceDisconnected 会立刻 set new deferred, 然后 yield 还回调度权
                            val beforeYieldedBinderDeferred = binderDeferred.value

                            yield()
                            val result = startServiceAndGetBinder()

                            val afterYieldedBinderDeferred = binderDeferred.value

                            // 如果在 yield 后, binderDeferred 已经被重新设置, 则说明 onServiceDisconnected 已经执行了
                            // fast path for disconnected.
                            if (beforeYieldedBinderDeferred != afterYieldedBinderDeferred) {
                                return@collectLatest
                            }

                            if (result is ServiceStartResult.AlreadyStarted) return@collectLatest
                            if (result is ServiceStartResult.Failure) {
                                logger.error { "Failed to start service after ${result.attempt} attempts." }
                                return@collectLatest
                            }
                            if (result !is ServiceStartResult.Success) error("unreachable condition")

                            logger.debug { "Service is connected: ${result.binder}" }

                            lock.withLock {
                                binderDeferred.value = CompletableDeferred()
                                binderDeferred.value.complete(result.binder)

                                isServiceConnected.value = true
                            }
                        }
                    } catch (_: CancellationException) {
                        isAtForeground.value = false
                        logger.debug { "App moved to background." }
                    }
                }
            }

            started = true
        }
    }
    
    /**
     * 服务已断开连接, 通信对象变为不可用.
     * 如果目前 APP 还在前台, 就要尝试重启并重连服务.
     */
    fun onServiceDisconnected() {
        scope.launch(
            CoroutineName("TorrentServiceConnection - ServiceDisconnected"),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            if (!isServiceConnected.value) {
                // 已经是断开状态，直接忽略
                return@launch
            }

            if (lock.tryLock()) { // 如果 lock 成功了, 说明 startServiceFlow collector 没在 lock 阶段
                try {
                    isServiceConnected.value = false
                    binderDeferred.value = CompletableDeferred()

                    yield() // 配合 startServiceFlow collector 中的 yield, 可以检测到 binderDeferred 的变化
                } finally {
                    lock.unlock()
                }
            } else {
                lock.withLock {
                    if (!isServiceConnected.value) {
                        // 已经是断开状态，直接忽略
                        return@launch
                    }

                    isServiceConnected.value = false
                    binderDeferred.value = CompletableDeferred()
                }
            }

            // 若应用仍想要连接，则重新启动
            if (isAtForeground.value) {
                logger.debug { "Service is disconnected and app is in foreground, restarting service connection in 1000 ms..." }
                delay(1000)
                startServiceFlow.value = Any()
            } else {
                logger.debug { "Service is disconnected and app is in background." }
            }
        }
    }

    /**
     * 启动服务并获取服务通信对象.
     *
     * This function has no side effect and can be safely cancelled.
     *
     * @return 如果服务已启动或超过最大尝试次数, 返回 `null`.
     */
    private suspend fun startServiceAndGetBinder(
        maxAttempts: Int = Int.MAX_VALUE, // 可根据需求设置
        intervalMillisBetweenAttempts: Long = 2500
    ): ServiceStartResult<T> {
        if (isServiceConnected.value) return ServiceStartResult.AlreadyStarted()
        var attempt = 0

        while (attempt < maxAttempts && !isServiceConnected.value) {
            try {
                if (isServiceConnected.value) return ServiceStartResult.AlreadyStarted()

                val result = starter.start()

                return ServiceStartResult.Success(result)
            } catch (ex: ServiceStartException) {
                logger.error(ex) { "[#$attempt] Failed to start service, retry after $intervalMillisBetweenAttempts ms" }

                attempt++
                delay(intervalMillisBetweenAttempts)

                continue
            }
        }

        if (isServiceConnected.value) return ServiceStartResult.AlreadyStarted()
        return ServiceStartResult.Failure(attempt)
    }

    fun close() {
        logger.debug { "Closing scope of TorrentServiceConnection." }
        isServiceConnected.value = false
        binderDeferred.value.cancel(CancellationException("Connection closed."))
        scope.cancel()
    }

    /**
     * 获取当前 binder 对象.
     *
     * - 如果服务还未连接, 此函数将一直挂起.
     */
    override suspend fun getBinder(): T {
        // 如果 isServiceDisconnected 为 false, 那 binderDeferred 一定是未完成的, 见 onServiceDisconnected
        // 如果 isServiceDisconnected 为 true, 那 binderDeferred 一定是已完成的, 见 startServiceWithRetry
        return binderDeferred.transformLatest {
            it.join()

            try {
                emit(it.await())
            } catch (_: CancellationException) {

            }
        }.first()
    }

    private sealed class ServiceStartResult<Binder : Any> {
        class Success<B : Any>(val binder: B) : ServiceStartResult<B>()
        class AlreadyStarted<B : Any> : ServiceStartResult<B>()
        class Failure<B : Any>(val attempt: Int) : ServiceStartResult<B>()
    }
}