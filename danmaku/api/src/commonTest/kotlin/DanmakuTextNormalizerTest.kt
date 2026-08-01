/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuTextNormalizerTest {
    private fun normalize(text: String) = DanmakuTextNormalizer.normalize(text)

    @Test
    fun `empty stays empty`() {
        assertEquals("", normalize(""))
    }

    @Test
    fun `plain text unchanged`() {
        assertEquals("前方高能", normalize("前方高能"))
    }

    @Test
    fun `trims trailing punctuation`() {
        assertEquals("前方高能", normalize("前方高能！！！"))
        assertEquals("前方高能", normalize("前方高能。"))
        assertEquals("前方高能", normalize("前方高能???"))
        assertEquals("前方高能", normalize("前方高能～～"))
        assertEquals("前方高能", normalize("前方高能…"))
    }

    @Test
    fun `does not trim leading punctuation`() {
        assertEquals("!前方高能", normalize("！前方高能"))
    }

    @Test
    fun `full width to half width`() {
        assertEquals("abc123", normalize("ａｂｃ１２３"))
        assertEquals("a b", normalize("a　b")) // ideographic space
    }

    @Test
    fun `lowercase latin`() {
        assertEquals("awsl", normalize("AWSL"))
        assertEquals("awsl", normalize("AwSl"))
    }

    @Test
    fun `collapse whitespace and trim`() {
        assertEquals("a b c", normalize("  a   b \t c  "))
    }

    @Test
    fun `all punctuation becomes empty`() {
        assertTrue(normalize("？？？").isEmpty())
        assertTrue(normalize("...").isEmpty())
        assertTrue(normalize("   ").isEmpty())
    }

    @Test
    fun `variants normalize to the same form`() {
        assertEquals(normalize("前方高能！"), normalize("前方高能 。"))
        assertEquals(normalize("AWSL~~~"), normalize("ａｗｓｌ"))
    }
}
