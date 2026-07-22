/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaProgressIndicatorTextTest {
    @Test
    fun `one times speed omits remaining time`() {
        assertEquals("01:00 / 10:00", renderSeconds(60, 600))
    }

    @Test
    fun `renders speed adjusted remaining time for short videos`() {
        assertEquals("01:00 / 10:00 (-04:30)", renderSeconds(60, 600, remainingSecs = 270))
    }

    @Test
    fun `renders speed adjusted remaining time for hour videos`() {
        assertEquals("01:00:00 / 02:00:00 (-00:30:00)", renderSeconds(3600, 7200, remainingSecs = 1800))
    }

    @Test
    fun `unknown total omits remaining time`() {
        assertEquals("00:42 / 00:00", renderSeconds(42, null, remainingSecs = 0))
    }

    @Test
    fun `reserve only includes remaining time in expanded mode`() {
        assertEquals("88:88 / 88:88", renderSecondsReserve(100, includeRemaining = false))
        assertEquals("88:88 / 88:88 (-88:88)", renderSecondsReserve(100, includeRemaining = true))
        assertEquals("88:88:88 / 88:88:88", renderSecondsReserve(3600, includeRemaining = false))
        assertEquals("88:88:88 / 88:88:88 (-88:88:88)", renderSecondsReserve(3600, includeRemaining = true))
    }

}
