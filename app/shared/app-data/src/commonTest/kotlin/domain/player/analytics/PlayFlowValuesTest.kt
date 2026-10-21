/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayFlowValuesTest {
    @Test
    fun `host is taken from the website url without www`() {
        assertEquals("xifan.tv", playFlowHost("https://www.xifan.tv/search?wd={keyword}"))
        assertEquals("mikanani.me", playFlowHost("https://mikanani.me"))
        assertEquals("example.com", playFlowHost("example.com/path"))
        assertNull(playFlowHost(null))
        assertNull(playFlowHost("  "))
    }

    @Test
    fun `tokens are packed into chunks within the length limit`() {
        assertEquals(emptyList(), packPlayFlowTokens(emptyList(), maxLength = 10))
        assertEquals(listOf("a=x,b=y"), packPlayFlowTokens(listOf("a=x", "b=y"), maxLength = 10))
        assertEquals(
            listOf("aaaa=x,b=y", "cccc=z"),
            packPlayFlowTokens(listOf("aaaa=x", "b=y", "cccc=z"), maxLength = 10),
        )
        // 超长的记号独占一段并被截断
        assertEquals(
            listOf("a=x", "0123456789", "b=y"),
            packPlayFlowTokens(listOf("a=x", "0123456789abc", "b=y"), maxLength = 10),
        )
    }
}
