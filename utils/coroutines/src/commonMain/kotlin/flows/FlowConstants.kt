/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("ObjectPropertyName", "NOTHING_TO_INLINE")

package me.him188.ani.utils.coroutines.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@PublishedApi
internal val _flowOfEmptyList = flowOf(emptyList<Any?>())

@Suppress("UNCHECKED_CAST")
inline fun <T> flowOfEmptyList(): Flow<List<T>> = _flowOfEmptyList as Flow<List<T>>

@PublishedApi
internal val sequenceOfEmptyString = sequenceOf("")

inline fun sequenceOfEmptyString(): Sequence<String> = sequenceOfEmptyString

@PublishedApi
internal val _flowOfNull = flowOf(null)

@Suppress("UNCHECKED_CAST")
inline fun <T> flowOfNull(): Flow<T?> = _flowOfNull as Flow<T?>
