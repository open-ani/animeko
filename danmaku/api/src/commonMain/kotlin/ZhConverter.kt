/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlinx.serialization.Serializable

/**
 * 弹幕的简繁转换方式.
 */
@Serializable
enum class ZhConversion {
    /**
     * 不转换.
     */
    NONE,

    /**
     * 转换为简体.
     */
    TO_SIMPLIFIED,

    /**
     * 转换为繁体.
     */
    TO_TRADITIONAL,
}

/**
 * 简繁转换.
 *
 * 从左到右扫描, 每个位置先查词组表 [ZhPhraseConversionTable] 取最长匹配, 没匹配上再退回单字表
 * [ZhConversionTable], 还是没有就原样保留. 词组表只收录了"逐字转换会转错"的条目, 于是像
 * "头发 -> 頭髮"、"饼干 -> 餅乾" 这种要看上下文的字也能转对.
 */
object ZhConverter {
    fun convert(text: String, conversion: ZhConversion): String {
        val charTable: Map<Char, Char>
        val phraseTable: Map<Char, ZhPhraseConversionTable.Bucket>
        when (conversion) {
            ZhConversion.NONE -> return text
            ZhConversion.TO_SIMPLIFIED -> {
                charTable = ZhConversionTable.traditionalToSimplified
                phraseTable = ZhPhraseConversionTable.traditionalToSimplified
            }

            ZhConversion.TO_TRADITIONAL -> {
                charTable = ZhConversionTable.simplifiedToTraditional
                phraseTable = ZhPhraseConversionTable.simplifiedToTraditional
            }
        }
        if (text.isEmpty()) return text

        // 绝大多数弹幕一个字都不用换, 所以只在真的遇到需要替换的字时才分配 StringBuilder.
        // 查词组表走 regionMatches, 匹配不上的位置不产生任何分配.
        var sb: StringBuilder? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]

            val bucket = phraseTable[c]
            if (bucket != null) {
                val matched = matchLongestPhrase(bucket, text, i)
                if (matched >= 0) {
                    if (sb == null) {
                        sb = StringBuilder(text.length)
                        sb.append(text, 0, i)
                    }
                    sb.append(bucket.values[matched])
                    i += bucket.keys[matched].length
                    continue
                }
            }

            val mapped = charTable[c]
            if (mapped == null) {
                sb?.append(c)
            } else {
                if (sb == null) {
                    sb = StringBuilder(text.length)
                    sb.append(text, 0, i)
                }
                sb.append(mapped)
            }
            i++
        }
        return sb?.toString() ?: text
    }

    /**
     * 在 [start] 处找出 [bucket] 里能匹配上的最长词组, 返回它在 [bucket] 中的下标, 没有则返回 -1.
     *
     * [bucket] 里的词组已按长度从长到短排好, 所以顺序扫到的第一个匹配就是最长匹配.
     */
    private fun matchLongestPhrase(
        bucket: ZhPhraseConversionTable.Bucket,
        text: String,
        start: Int,
    ): Int {
        val remaining = text.length - start
        val keys = bucket.keys
        for (index in keys.indices) {
            val key = keys[index]
            if (key.length > remaining) continue // 排在前面的都是太长放不下的
            if (text.regionMatches(start, key, 0, key.length)) return index
        }
        return -1
    }
}
