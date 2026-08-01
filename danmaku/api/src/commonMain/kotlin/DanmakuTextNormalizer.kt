/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

/**
 * 把弹幕文本归一化成用于"判断两条弹幕是不是同一句话"的形式.
 *
 * 归一化只用于比较 (合并重复弹幕, 关键词过滤), 不会影响实际显示的文本.
 *
 * 规则:
 * - 全角 ASCII (`！`, `Ａ`, `　` 等) 转半角;
 * - 连续空白折叠成一个空格;
 * - 拉丁字母转小写;
 * - 去掉首尾空白, 以及结尾的语气标点 (`。`, `!`, `?`, `~`, `…` 等).
 *
 * 注意: 如果一条弹幕全部由这些标点组成 (例如 `？？？`), 归一化会得到空串.
 * 调用方应当把空串视为"无法比较", 即该弹幕独一无二, 而不是把它们全部合并到一起.
 *
 * @see DanmakuMerger
 */
object DanmakuTextNormalizer {
    /**
     * 结尾可以被裁掉的字符. 注意这里是在全角转半角之后再判断的, 所以 `！`/`？`/`～`/`，`/`．` 已经变成了半角形式.
     */
    private const val TRIMMABLE_TAIL = " \t.,!?~;:'\"。、…⋯・･！？"

    fun normalize(text: String): String {
        if (text.isEmpty()) return ""

        val sb = StringBuilder(text.length)
        var lastIsSpace = true // 初始为 true, 顺便去掉开头的空白
        for (c in text) {
            val half = toHalfWidth(c)
            if (half.isWhitespace()) {
                if (!lastIsSpace) {
                    sb.append(' ')
                    lastIsSpace = true
                }
            } else {
                lastIsSpace = false
                sb.append(half.lowercaseChar())
            }
        }

        var end = sb.length
        while (end > 0 && sb[end - 1] in TRIMMABLE_TAIL) {
            end--
        }
        if (end == sb.length) {
            return sb.toString()
        }
        return sb.substring(0, end)
    }

    /**
     * 全角 -> 半角. 只处理 ASCII 对应的那一段, 中文标点 (`。`, `、`) 不在其中.
     */
    private fun toHalfWidth(c: Char): Char = when (val code = c.code) {
        0x3000 -> ' ' // IDEOGRAPHIC SPACE
        in 0xFF01..0xFF5E -> (code - 0xFEE0).toChar()
        else -> c
    }
}
