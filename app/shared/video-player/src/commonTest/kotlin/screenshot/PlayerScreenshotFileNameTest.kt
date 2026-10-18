/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.screenshot

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerScreenshotFileNameTest {
    @Test
    fun `position is formatted as minutes seconds and millis`() {
        assertEquals("123-01-1m23s456ms.png", playerScreenshotFileName(123, "01", 83_456))
        assertEquals("123-12-0m0s999ms.png", playerScreenshotFileName(123, "12", 999))
        assertEquals("123-12-125m0s0ms.png", playerScreenshotFileName(123, "12", 125 * 60_000L))
    }

    @Test
    fun `negative positions are treated as zero`() {
        assertEquals("1-SP-0m0s0ms.png", playerScreenshotFileName(1, "SP", -5))
    }

    @Test
    fun `episode sort is sanitized for file names`() {
        assertEquals("7-SP_1-0m0s0ms.png", playerScreenshotFileName(7, "SP/1", 0))
        assertEquals("7-a_b-0m0s0ms.png", playerScreenshotFileName(7, "a:b", 0))
        assertEquals("7-0-0m0s0ms.png", playerScreenshotFileName(7, "", 0))
    }
}
