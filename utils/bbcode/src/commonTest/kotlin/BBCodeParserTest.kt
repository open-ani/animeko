/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.bbcode

import kotlin.test.assertEquals
import kotlin.test.assertIs

abstract class BBCodeParserTestHelper {
    inline fun <reified T> List<RichElement>.at(index: Int): T {
        val value = this[index]
        assertIs<T>(value)
        return value
    }

    fun assertText(
        element: RichElement.Text,
        jumpUrl: String? = null,
        value: String,
        size: Int = RichElement.Text.DEFAULT_SIZE,
        color: String? = null,
        italic: Boolean = false,
        underline: Boolean = false,
        strikethrough: Boolean = false,
        bold: Boolean = false,
        mask: Boolean = false,
        code: Boolean = false,
    ) {
        assertElement(jumpUrl, element)
        assertEquals(value, element.value)
        assertEquals(size, element.size)
        assertEquals(color, element.color)
        assertEquals(italic, element.italic)
        assertEquals(underline, element.underline)
        assertEquals(strikethrough, element.strikethrough)
        assertEquals(bold, element.bold)
        assertEquals(mask, element.mask)
        assertEquals(code, element.code)
    }

    fun assertImage(
        element: RichElement.Image,
        jumpUrl: String? = null,
        imageUrl: String,
        height: Int? = null,
        width: Int? = null,
    ) {
        assertElement(jumpUrl, element)
        assertEquals(imageUrl, element.imageUrl)
        assertEquals(height, element.height)
        assertEquals(width, element.width)
    }

    class QuoteContext(
        val elements: List<RichElement>,
    )

    inline fun assertQuote(
        element: RichElement.Quote,
        jumpUrl: String? = null,
        block: QuoteContext.() -> Unit,
    ) {
        assertElement(jumpUrl, element)
        block(QuoteContext(element.contents.elements))
    }

    fun assertBangumiSticker(
        element: RichElement.BangumiSticker,
        jumpUrl: String? = null,
        id: Int,
    ) {
        assertElement(jumpUrl, element)
        assertEquals(id, element.id)
    }

    fun assertKanmoji(
        element: RichElement.Kanmoji,
        jumpUrl: String? = null,
        id: String,
    ) {
        assertElement(jumpUrl, element)
        assertEquals(id, element.id)
    }

    fun assertElement(
        jumpUrl: String?,
        element: RichElement
    ) {
        assertEquals(jumpUrl, element.jumpUrl)
    }
}
