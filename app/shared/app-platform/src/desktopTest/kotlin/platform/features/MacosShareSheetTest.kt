/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import kotlin.test.Test
import kotlin.test.assertEquals

class MacosShareSheetTest {
    @Test
    fun `anchor is flipped into a bottom-left origin for an unflipped view`() {
        // 视图高 500: 顶部在 100、高 40 的按钮, 底边在 AppKit 坐标里是 500 - 140 = 360
        val rect = ShareAnchor(left = 24f, top = 100f, width = 80f, height = 40f).toAppKitRect(viewHeight = 500f, flipped = false)
        assertEquals(ShareAnchor(left = 24f, top = 360f, width = 80f, height = 40f), rect)
    }

    @Test
    fun `anchor is kept as is for a flipped view`() {
        val anchor = ShareAnchor(left = 24f, top = 100f, width = 80f, height = 40f)
        assertEquals(anchor, anchor.toAppKitRect(viewHeight = 500f, flipped = true))
    }
}
