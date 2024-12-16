/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * @see Flow.restartable
 */
class FlowRestarter {
    internal val id = MutableStateFlow(0)

    /**
     * 重启 flow. 这将会导致 flow 从上游最开始的地方重新开始, 例如执行所有的 `map`, `filter` 等操作.
     */
    fun restart() {
        id.update { it + 1 }
    }
}

/**
 * 使 flow 可以被重启. 等价于:
 *
 * ```
 * private val id = MutableStateFlow(0) // FlowRestarter.restart 相当于 id.value++
 *
 * val result = id.flatMapLatest { _ ->
 *    upstream.map { ... }...
 *    // 重启时, 会重新执行上面的 map, filter 等操作
 * }
 * ```
 */
fun <T> Flow<T>.restartable(restarter: FlowRestarter): Flow<T> = restarter.id.flatMapLatest { this }


/**
 * Flow 是否正在运行中的监听器.
 * @see withRunning
 */
class FlowRunning {
    @Suppress("PropertyName")
    @PublishedApi
    internal val _isRunning: MutableStateFlow<Int> = MutableStateFlow<Int>(0)

    /**
     * 是否有至少一个 [withRunning] 正在运行.
     */
    val isRunning = _isRunning.map { it > 0 }

    /**
     * 执行 [block], 并在执行期间将 [isRunning] 设置为 `true`, 执行完毕后设置为 `false`.
     *
     * 此函数线程是线程安全的. 多个线程/协程同时调用 [withRunning], [isRunning] 将保持为 `true`, 直到它们全部结束运行.
     */
    inline fun <R> withRunning(block: () -> R): R {
        _isRunning.update { it + 1 }
        try {
            return block()
        } finally {
            _isRunning.update { it - 1 }
        }
    }
}

///**
// * 增加一个监控, 以获知此 flow 是否正在运行.
// */
//fun <T> Flow<T>.isRunning(running: FlowRunning) =
//    this.onStart { running._isRunning.value = true }
//        .onCompletion { running._isRunning.value = false }
