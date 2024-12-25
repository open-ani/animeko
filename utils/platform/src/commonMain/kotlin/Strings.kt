/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform

import androidx.collection.IntSet
import kotlin.math.round

/**
 * Equivalent to `String.format("%.1f", value)`
 */
fun String.Companion.format1f(value: Double): String {
    return (round(value * 10) / 10.0).toString()
}

fun String.Companion.format1f(value: Float): String {
    return (round(value * 10) / 10.0).toString()
}

/**
 * Equivalent to `String.format("%.2f", value)`
 */
fun String.Companion.format2f(value: Double): String {
    return (round(value * 100) / 100.0).toString()
}

/**
 * Equivalent to `String.format("%.2f", value)`
 */
fun String.Companion.format2f(value: Float): String {
    return (round(value * 100) / 100.0).toString()
}

/**
 * 删除 `StringBuilder` 中所有匹配指定正则表达式的子字符串。
 *
 * 示例：
 * ```kotlin
 * val sb = StringBuilder("abc123def456ghi")
 * sb.deleteMatches(Regex("\\d+")) // 结果为 "abcdefghi"
 * ```
 */
fun StringBuilder.deleteMatches(regex: Regex): StringBuilder {
    var matchResult = regex.find(this)
    while (matchResult != null) {
        deleteRange(matchResult.range.first, matchResult.range.last + 1)
        matchResult = regex.find(this, matchResult.range.first)
    }
    return this
}

/**
 * 删除 `StringBuilder` 中所有包含在指定 set 中的字符.
 *
 * 示例：
 * ```kotlin
 * val sb = StringBuilder("abc123def456ghi")
 * sb.deleteAnyCharIn("acf") // returns "b123de456ghi"
 * ```
 * 
 * @param chars [Char.code] 的 set
 */
fun StringBuilder.deleteAnyCharIn(chars: IntSet): StringBuilder {
    var index = 0
    while (index < length) {
        if (this[index].code in chars) {
            deleteAt(index)
        } else {
            index++
        }
    }
    return this
}

fun StringBuilder.deletePrefix(prefix: String): StringBuilder {
    if (startsWith(prefix)) {
        deleteRange(0, prefix.length)
    }
    return this
}

fun StringBuilder.deleteSuffix(suffix: String): StringBuilder {
    if (endsWith(suffix)) {
        deleteRange(length - suffix.length, length)
    }
    return this
}

fun StringBuilder.deleteInfix(infix: String): StringBuilder {
    val index = indexOf(infix)
    if (index != -1) {
        deleteRange(index, index + infix.length)
    }
    return this
}

inline fun StringBuilder.replaceMatches(regex: Regex, replacement: (MatchResult) -> String): StringBuilder {
    val matches = regex.findAll(this)
    val result = StringBuilder()
    var lastEnd = 0
    for (match in matches) {
        result.append(substring(lastEnd, match.range.first))
        result.append(replacement(match))
        lastEnd = match.range.last + 1
    }
    result.append(substring(lastEnd))
    clear().append(result)
    return this
}

fun StringBuilder.trimSB(): StringBuilder {
    var start = 0
    while (start < length && this[start].isWhitespace()) {
        start++
    }
    deleteRange(0, start)
    var end = length
    while (end > 0 && this[end - 1].isWhitespace()) {
        end--
    }
    deleteRange(end, length)
    return this
}
