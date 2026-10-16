/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateClientPlatformTest {
    @Test
    fun `android tv requests tv packages`() {
        assertEquals("androidtv", Platform.Android(Arch.ARMV8A, isTv = true).updateClientPlatform())
    }

    @Test
    fun `other platforms use their lowercase name`() {
        assertEquals("android", Platform.Android(Arch.ARMV8A).updateClientPlatform())
        assertEquals("windows", Platform.Windows(Arch.X86_64).updateClientPlatform())
        assertEquals("macos", Platform.MacOS(Arch.AARCH64).updateClientPlatform())
        assertEquals("ios", Platform.Ios.updateClientPlatform())
    }
}
