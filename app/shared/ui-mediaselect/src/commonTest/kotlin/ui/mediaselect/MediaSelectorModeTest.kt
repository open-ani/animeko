/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaSelectorModeTest {
    @Test
    fun `auto match always stays in the side sheet`() {
        assertFalse(MediaSelectorMode.AUTO.needsContainer(200.dp))
        assertFalse(MediaSelectorMode.AUTO.needsContainer(800.dp))
    }

    @Test
    fun `manual and bt use a container only when the side sheet is shorter than 480dp`() {
        for (mode in listOf(MediaSelectorMode.MANUAL, MediaSelectorMode.BT)) {
            assertTrue(mode.needsContainer(448.dp), "$mode at 448dp")
            assertFalse(mode.needsContainer(480.dp), "$mode at 480dp")
            assertFalse(mode.needsContainer(800.dp), "$mode at 800dp")
        }
    }
}
