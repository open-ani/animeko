/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.bbcode


sealed interface RichElement {
    // 如果修改任意 RichElement 的 properties, 需要同时修改 BBCodeParserTestHelper 中的各个 assert 方法, 以及 BBCodeTestGenerator 为该类型元素生成 assert
    
    /**
     * 该元素的可点击跳转链接，若为 null 则表示不可跳转
     */
    val jumpUrl: String?

    data class Text(
        val value: String,
        val size: Int = DEFAULT_SIZE,
        /**
         * Can be:
         * - RGBA: "#FFFFFFFF"
         * - RGB: "#FFFFFF"
         * - name: "red"
         *
         * 如果无法解析则显示为默认颜色
         */
        val color: String? = null,

        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val bold: Boolean = false,

        val mask: Boolean = false,
        val code: Boolean = false,

        override val jumpUrl: String? = null
    ) : RichElement {
        companion object {
            const val DEFAULT_SIZE = 16
        }
    }

    data class Image(
        val imageUrl: String,
        val width: Int? = null,
        val height: Int? = null,
        override val jumpUrl: String? = null
    ) : RichElement

    data class Quote(
        val contents: RichText,
        override val jumpUrl: String? = null
    ) : RichElement

    data class BangumiSticker(
        val id: Int,
        override val jumpUrl: String? = null
    ) : RichElement

    data class Kanmoji(
        val id: String, // "(=A=)", 详情见文法
        override val jumpUrl: String? = null
    ) : RichElement
}