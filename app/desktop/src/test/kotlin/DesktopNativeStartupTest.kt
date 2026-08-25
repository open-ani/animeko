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
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNativeStartupTest {
    @Test
    fun `player is prepared after JCEF when requested`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = false,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("JCEF", "player"), calls)
    }

    @Test
    fun `player is prepared before JCEF when requested`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = true,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("player", "JCEF"), calls)
    }

    @Test
    fun `Intel macOS prepares player after JCEF`() {
        assertFalse(shouldPreparePlayerBeforeJcef(Platform.MacOS(Arch.X86_64)))
    }

    @Test
    fun `other desktop platforms prepare player before JCEF`() {
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.MacOS(Arch.AARCH64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.X86_64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.AARCH64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Linux(Arch.X86_64)))
    }
}
