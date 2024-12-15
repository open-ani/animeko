/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UNCHECKED_CAST")

package me.him188.ani.utils.coroutines.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector


/**
 * Returns a [Flow] whose values are generated by [transform] function that process the most recently emitted values by each flow.
 */
fun <T1, T2, T3, T4, T5, T6, R> combineTransform(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2, T3, T4, T5, T6) -> Unit
): Flow<R> = kotlinx.coroutines.flow.combineTransform(flow, flow2, flow3, flow4, flow5, flow6) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
    )
}


/**
 * Returns a [Flow] whose values are generated by [transform] function that process the most recently emitted values by each flow.
 */
fun <T1, T2, T3, T4, T5, T6, T7, R> combineTransform(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2, T3, T4, T5, T6, T7) -> Unit
): Flow<R> =
    kotlinx.coroutines.flow.combineTransform(flow, flow2, flow3, flow4, flow5, flow6, flow7) { args: Array<*> ->
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
        )
    }


/**
 * Returns a [Flow] whose values are generated by [transform] function that process the most recently emitted values by each flow.
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combineTransform(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2, T3, T4, T5, T6, T7, T8) -> Unit
): Flow<R> =
    kotlinx.coroutines.flow.combineTransform(flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8) { args: Array<*> ->
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
            args[7] as T8,
        )
    }


/**
 * Returns a [Flow] whose values are generated by [transform] function that process the most recently emitted values by each flow.
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combineTransform(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2, T3, T4, T5, T6, T7, T8, T9) -> Unit
): Flow<R> = kotlinx.coroutines.flow.combineTransform(
    flow,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
    flow7,
    flow8,
    flow9,
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
    )
}


/**
 * Returns a [Flow] whose values are generated by [transform] function that process the most recently emitted values by each flow.
 */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combineTransform(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> Unit
): Flow<R> = kotlinx.coroutines.flow.combineTransform(
    flow,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
    flow7,
    flow8,
    flow9,
    flow10,
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
    )
}