/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isAndroidTv
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerKernelConfigTest {
    /**
     * 默认值跟随平台: TV 上默认关闭预先加载画质增强着色器, 其他平台默认开启.
     * 各平台的 TV 判定见 [me.him188.ani.utils.platform.isAndroidTv].
     */
    @Test
    fun `default exoPlayerInitEffectGraphInAdvance matches current platform`() {
        val expected = !currentPlatform().isAndroidTv()

        assertEquals(expected, PlayerKernelConfig().exoPlayerInitEffectGraphInAdvance)
        assertEquals(expected, PlayerKernelConfig.Default.exoPlayerInitEffectGraphInAdvance)
    }
}
