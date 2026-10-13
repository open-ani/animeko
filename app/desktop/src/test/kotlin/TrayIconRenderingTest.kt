/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.composenativetray.utils.ComposableIconUtils
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TrayIconRenderingTest {
    @Test
    fun `tray icon encodes to PNG with the desktop Skiko runtime`() {
        SwingUtilities.invokeAndWait {
            // Exercise both encoding paths, including the 32px Windows tray icon.
            for (targetSize in listOf(192, 32)) {
                val png = ComposableIconUtils.renderComposableToPngBytes(
                    IconRenderProperties(targetWidth = targetSize, targetHeight = targetSize),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Red)
                    }
                }

                val image = assertNotNull(ImageIO.read(ByteArrayInputStream(png)))
                assertEquals(targetSize, image.width)
                assertEquals(targetSize, image.height)
                assertEquals(0xFFFF0000.toInt(), image.getRGB(targetSize / 2, targetSize / 2))
            }
        }
    }
}
