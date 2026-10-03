/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class MacosShareSheetTest {
    private val anchor = DpRect(left = 24.dp, top = 100.dp, right = 104.dp, bottom = 140.dp)

    @Test
    fun `anchor is flipped into a bottom-left origin for an unflipped view`() {
        // 视图高 500: 顶部在 100、底部在 140 的按钮, 在 AppKit 坐标里占 360..400
        assertEquals(
            DpRect(left = 24.dp, top = 360.dp, right = 104.dp, bottom = 400.dp),
            anchor.toAppKitRect(viewHeight = 500f, flipped = false),
        )
    }

    @Test
    fun `anchor is kept as is for a flipped view`() {
        assertEquals(anchor, anchor.toAppKitRect(viewHeight = 500f, flipped = true))
    }
}
