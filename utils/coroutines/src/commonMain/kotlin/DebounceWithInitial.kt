package me.him188.ani.utils.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 等同于 [debounce], 但是会直接 emit 第一个值, 随后再开始 debounce.
 *
 * 适用于 StateFlow 情况. 这可以让 collector StateFlow 的丢一个
 */
@OverloadResolutionByLambdaReturnType
@JvmName("debounceWithInitialDuration")
fun <T> Flow<T>.debounceWithInitial(
    timeout: () -> Duration,
): Flow<T> {
    val isInitial = object {
        @Volatile
        var value = true
    }
    return debounce {
        if (isInitial.value) {
            isInitial.value =  false
            Duration.ZERO
        } else {
            timeout()
        }
    }
}

/**
 * @see debounceWithInitial
 */
@OverloadResolutionByLambdaReturnType
fun <T> Flow<T>.debounceWithInitial(timeoutMillis: () -> Long): Flow<T> =
    debounceWithInitial { timeoutMillis().milliseconds }

/**
 * @see debounceWithInitial
 */
fun <T> Flow<T>.debounceWithInitial(timeout: Duration): Flow<T> =
    debounceWithInitial { timeout }

/**
 * @see debounceWithInitial
 */
fun <T> Flow<T>.debounceWithInitial(timeoutMillis: Long): Flow<T> =
    debounceWithInitial { timeoutMillis.milliseconds }
