/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.runningFold
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


fun <T> Flow<T>.runningList(): Flow<List<T>> {
    return runningFold(emptyList()) { acc, value ->
        acc + value
    }
}

fun combineOr(vararg flows: Flow<Boolean>): Flow<Boolean> = combine(*flows) { flows -> flows.any { it } }

fun <T, R> Flow<T>.runningFoldNoInitialEmit(
    initial: R,
    @BuilderInference operation: suspend (accumulator: R, value: T) -> R
): Flow<R> = flow {
    var accumulator: R = initial
    collect { value ->
        accumulator = operation(accumulator, value)
        emit(accumulator)
    }
}

/**
 * Maps each value to a [R] using the given [mapper] function, and closes the previous [R] if any.
 */
inline fun <T, R : AutoCloseable> Flow<T>.mapAutoClose(
    @BuilderInference crossinline mapper: suspend (T) -> R
): Flow<R> = flow {
    var last: R? = null
    collect { value ->
        last?.close()
        val new = mapper(value)
        emit(new)
        last = new
    }
}

/**
 * Maps each value to a [R] using the given [mapper] function, and closes the previous [R] if any.
 */
inline fun <T, R : AutoCloseable, C : Collection<R>> Flow<T>.mapAutoCloseCollection(
    @BuilderInference crossinline mapper: suspend (T) -> C
): Flow<C> = flow {
    var last: C? = null
    collect { value ->
        last?.forEach { it.close() }
        val new = mapper(value)
        emit(new)
        last = new
    }
}

fun <T : AutoCloseable?> Flow<T>.closeOnReplacement(): Flow<T> = flow {
    var last: T? = null
    collect { value ->
        last?.close()
        emit(value)
        last = value
    }
}

/**
 * 每当新值到来时, 调用 [onReplace] 上一个值.
 */
fun <T> Flow<T>.onReplacement(
    onReplace: suspend (T) -> Unit
): Flow<T> = flow {
    var last: T? = null
    collect { value ->
        last?.let { onReplace(it) }
        emit(value)
        last = value
    }
}

/**
 * Maps each value to a [R] using the given [mapper] function, and closes the previous [R] if any.
 */
inline fun <T, R : AutoCloseable> Flow<T>.mapNotNullAutoClose(
    @BuilderInference crossinline mapper: suspend (T) -> R?
): Flow<R> = flow {
    var last: R? = null
    collect { value ->
        last?.close()
        val new = mapper(value)
        if (new != null) {
            emit(new)
        }
        last = new
    }
}

/**
 * A [coroutineScope] that can be [cancelled][CancellableCoroutineScope.cancelScope]
 * without causing the [coroutineScope] to throw a [CancellationException].
 */
suspend inline fun <R> cancellableCoroutineScope(
    onCancel: () -> R,
    crossinline block: suspend CancellableCoroutineScope.() -> R
): R {
    val owner = Any()
    return try {
        coroutineScope {
            val self = this
            block(
                object : CancellableCoroutineScope {
                    override fun cancelScope() {
                        self.cancel(OwnedCancellationException(owner))
                    }

                    override val coroutineContext: CoroutineContext = self.coroutineContext
                },
            )
        }
    } catch (e: OwnedCancellationException) {
        e.checkOwner(owner)
        onCancel()
    }
}

/**
 * A [coroutineScope] that can be [cancelled][CancellableCoroutineScope.cancelScope]
 * without causing the [coroutineScope] to throw a [CancellationException].
 */
suspend inline fun <R> cancellableCoroutineScope(
    crossinline block: suspend CancellableCoroutineScope.() -> R
): R? {
    return cancellableCoroutineScope(
        onCancel = { null },
        block = block,
    )
}

interface CancellableCoroutineScope : CoroutineScope {
    fun cancelScope()
}


class OwnedCancellationException(val owner: Any) : CancellationException("Aborted by $owner")

fun OwnedCancellationException.checkOwner(owner: Any) {
    if (this.owner !== owner) throw this
}

/**
 * 一直重复直到成功. 每次重试前等待 [duration].
 */
fun <T> Flow<T>.retryEvery(duration: Duration): Flow<T> = retry { cause ->
    if (cause is CancellationException) {
        return@retry false
    }
    delay(duration)
    return@retry true
}

/**
 * 一直重复直到成功. 每次重试前等待指数退避时间.
 */
fun <T> Flow<T>.retryUntilSuccess(maxWait: Duration = 10.seconds): Flow<T> {
    require(maxWait > 0.seconds)
    var duration = 1.seconds
    return retry { cause ->
        if (cause is CancellationException) {
            return@retry false
        }
        delay(duration)
        duration *= 2
        duration = duration.coerceAtMost(maxWait)
        return@retry true
    }
}