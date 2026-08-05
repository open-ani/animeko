/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNativeStartupTest {
    @Test
    fun `VLC is prepared after JCEF`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            usesMpv = false,
            prepareMpv = { error("mpv must not be prepared on a VLC platform") },
            initializeJcef = { calls += "JCEF" },
            prepareVlc = { calls += "VLC" },
        )

        assertEquals(listOf("JCEF", "VLC"), calls)
    }

    @Test
    fun `mpv is prepared before JCEF`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            usesMpv = true,
            prepareMpv = { calls += "mpv" },
            initializeJcef = { calls += "JCEF" },
            prepareVlc = { error("VLC must not be prepared on an mpv platform") },
        )

        assertEquals(listOf("mpv", "JCEF"), calls)
    }
}
