/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform.collections

import androidx.collection.IntList
import androidx.collection.MutableIntList

inline fun <T> Collection<T>.mapToIntList(
    transform: (T) -> Int,
): IntList {
    val array = MutableIntList(size)
    forEach { array.add(transform(it)) }
    return array
}

inline fun <V, M : MutableMap<in Int, in V>> IntList.associateWithTo(
    destination: M,
    valueSelector: (Int) -> V
): M {
    this.forEach { element ->
        destination.put(element, valueSelector(element))
    }
    return destination
}

fun IntList.toIntArray(): IntArray {
    val array = IntArray(size)
    forEachIndexed { index, element ->
        array[index] = element
    }
    return array
}