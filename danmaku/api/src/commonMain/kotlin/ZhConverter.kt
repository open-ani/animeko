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
 * 简繁转换. 只做单字映射, 不做词组分词, 见 [ZhConversionTable].
 */
object ZhConverter {
    fun convert(text: String, conversion: ZhConversion): String {
        val table = when (conversion) {
            ZhConversion.NONE -> return text
            ZhConversion.TO_SIMPLIFIED -> ZhConversionTable.traditionalToSimplified
            ZhConversion.TO_TRADITIONAL -> ZhConversionTable.simplifiedToTraditional
        }
        if (text.isEmpty()) return text

        // 绝大多数弹幕一个字都不用换, 所以只在真的遇到需要替换的字时才分配 StringBuilder
        var sb: StringBuilder? = null
        for (i in text.indices) {
            val c = text[i]
            val mapped = table[c]
            if (mapped == null) {
                sb?.append(c)
            } else {
                if (sb == null) {
                    sb = StringBuilder(text.length)
                    sb.append(text, 0, i)
                }
                sb.append(mapped)
            }
        }
        return sb?.toString() ?: text
    }
}
