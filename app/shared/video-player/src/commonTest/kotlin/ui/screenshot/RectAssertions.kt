/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.ui.geometry.Rect
import kotlin.test.assertEquals

internal fun assertRect(expected: Rect, actual: Rect, tolerance: Float = 0.5f) {
    assertEquals(expected.left, actual.left, tolerance, "left")
    assertEquals(expected.top, actual.top, tolerance, "top")
    assertEquals(expected.right, actual.right, tolerance, "right")
    assertEquals(expected.bottom, actual.bottom, tolerance, "bottom")
}
