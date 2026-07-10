/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 手动检查预览浮窗渲染: 输出 PNG 供人工/agent 检查布局 (图片应完整在气泡内).
 */
@OptIn(ExperimentalTestApi::class)
class PreviewPopupScreenshotTest {

    private fun solidFrame(color: Color, width: Int = 160, height: Int = 90): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.color = color })
        return bitmap
    }

    @Test
    fun `dump popup rendering`() = runAniComposeUiTest {
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = { solidFrame(Color.Green) },
            debounceMillis = 0,
        )
        setContent {
            MediaProgressSlider(
                PlayerProgressSliderState(
                    currentPositionMillis = { 30_000L },
                    totalDurationMillis = { 100_000L },
                    chapters = { emptyList() },
                    onPreview = {},
                    onPreviewFinished = {},
                ),
                cacheProgressInfoFlow = { null },
                framePreview = framePreview,
            )
        }
        waitForIdle()
        runOnUiThread {
            onNodeWithTag(TAG_PROGRESS_SLIDER).performMouseInput { moveTo(center) }
        }
        runOnIdle {
            waitUntil(timeoutMillis = 5_000) {
                onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists()
            }
        }
        // 等 animateContentSize 完成
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val popupNode = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true)
        val image = popupNode.captureToImage()
        val out = File(System.getProperty("java.io.tmpdir"), "preview-popup.png")
        ImageIO.write(image.toAwtImage(), "png", out)
        println("POPUP_PNG=${out.absolutePath} size=${image.width}x${image.height}")

        // 图片必须完整落在浮窗内 (回归: clip 形状错误曾把图片顶部裁出气泡).
        val popupBounds = popupNode.fetchSemanticsNode().boundsInWindow
        val frameBounds = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            frameBounds.top >= popupBounds.top && frameBounds.bottom <= popupBounds.bottom &&
                frameBounds.left >= popupBounds.left && frameBounds.right <= popupBounds.right,
            "frame $frameBounds must be inside popup $popupBounds",
        )
    }
}
