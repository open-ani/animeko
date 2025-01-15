/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.client.infrastructure

typealias MultiValueMap = MutableMap<String, List<String>>

fun collectionDelimiter(collectionFormat: String) = when (collectionFormat) {
    "csv" -> ","
    "tsv" -> "\t"
    "pipe" -> "|"
    "space" -> " "
    else -> ""
}

val defaultMultiValueConverter: (item: Any?) -> String = { item -> "$item" }

fun <T : Any?> toMultiValue(
    items: Array<T>,
    collectionFormat: String,
    map: (item: T) -> String = defaultMultiValueConverter
) = toMultiValue(items.asIterable(), collectionFormat, map)

fun <T : Any?> toMultiValue(
    items: Iterable<T>,
    collectionFormat: String,
    map: (item: T) -> String = defaultMultiValueConverter
): List<String> {
    return when (collectionFormat) {
        "multi" -> items.map(map)
        else -> listOf(items.joinToString(separator = collectionDelimiter(collectionFormat), transform = map))
    }
}
