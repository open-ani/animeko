/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageUtilsTest {
    @Test
    fun testImageBitmapCropCoordinatesAndDimensions() {
        val original = ImageBitmap(width = 4, height = 2)
        val canvas = Canvas(original)
        canvas.drawRect(Rect(Offset.Zero, Size(2f, 2f)), Paint().apply { color = Color.Red })
        canvas.drawRect(Rect(Offset(2f, 0f), Size(2f, 2f)), Paint().apply { color = Color.Blue })

        val cropped = original.crop(x = 2, y = 0, width = 2, height = 2)
        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertEquals(Color.Blue, cropped.toPixelMap()[0, 0])
        assertEquals(Color.Blue, cropped.toPixelMap()[1, 1])
    }
}
