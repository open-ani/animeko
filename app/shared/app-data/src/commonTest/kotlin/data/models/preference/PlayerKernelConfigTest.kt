/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerKernelConfigTest {
    @Test
    fun `android tv disables preinit effect graph by default`() {
        val config = PlayerKernelConfig.forPlatform(Platform.Android(Arch.ARMV8A, isTv = true))

        assertFalse(config.exoPlayerInitEffectGraphInAdvance)
    }

    @Test
    fun `other platforms keep preinit effect graph enabled by default`() {
        assertTrue(PlayerKernelConfig.forPlatform(Platform.Android(Arch.ARMV8A)).exoPlayerInitEffectGraphInAdvance)
        assertTrue(PlayerKernelConfig.forPlatform(Platform.Ios).exoPlayerInitEffectGraphInAdvance)
        assertTrue(PlayerKernelConfig.forPlatform(Platform.Windows(Arch.X86_64)).exoPlayerInitEffectGraphInAdvance)
        assertTrue(PlayerKernelConfig.forPlatform(Platform.MacOS(Arch.AARCH64)).exoPlayerInitEffectGraphInAdvance)
    }
}
