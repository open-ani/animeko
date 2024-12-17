/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

suspend inline fun <T, R> FlowCollector<List<T>>.runOrEmitEmptyList(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    try {
        return block()
    } catch (e: Throwable) {
        emit(emptyList())
        throw e
    }
}

/**
 * 将 [this] 的成功结果封装为 [Result.success], 异常结果封装为 [Result.failure].
 * 于是, 返回的 flow 将不会抛出异常 (除了 [CancellationException]).
 */
fun <T> Flow<T>.catching(): Flow<Result<T>> = map {
    Result.success(it)
}.catch {
    if (it is CancellationException) {
        throw it
    }

    emit(Result.failure(it))
}

/**
 * 类似于 [shareIn], 但是会将上游异常传递给下游.
 *
 * 注意, 如果 [started] 配置为 [SharingStarted.WhileSubscribed] 而其 `replayExpirationMillis != 0` 并且 [replay] != 0,
 * 则每次重新 collect 都会传递上次发生的异常, 而导致 flow 永远不会 restart.
 */
fun <T> Flow<T>.shareTransparentlyIn(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 0,
) = this
    .catching()
    .shareIn(
        scope, started, replay,
    )
    .map {
        it.getOrThrow() // 透明异常. 上游的异常传递给下游
    }
