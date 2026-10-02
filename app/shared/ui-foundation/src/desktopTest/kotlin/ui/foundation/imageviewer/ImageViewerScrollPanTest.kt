/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.ZoomState
import com.github.panpf.zoomimage.compose.rememberZoomState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.imageScrollPan
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.framework.runOnSwingEdt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 桌面端滚轮 / 触摸板滚动: 不按修饰键平移, Ctrl/Cmd 缩放.
 */
@OptIn(ExperimentalTestApi::class)
class ImageViewerScrollPanTest {
    private fun runScrollTest(block: androidx.compose.ui.test.ComposeUiTest.(ZoomState) -> Unit) = runOnSwingEdt {
        runAniComposeUiTest {
            lateinit var zoomState: ZoomState
            // 有固有尺寸的图片, zoomimage 才有 min/max 缩放范围
            val painter = BitmapPainter(ImageBitmap(800, 600))
            setContent {
                ProvideCompositionLocalsForPreview {
                    zoomState = rememberZoomState()
                    ZoomImage(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(400.dp, 300.dp).testTag("image").imageScrollPan(zoomState.zoomable),
                        zoomState = zoomState,
                    )
                }
            }
            waitUntil(timeoutMillis = 5_000) { zoomState.zoomable.maxScale > zoomState.zoomable.minScale && zoomState.zoomable.transform.scaleX > 0f }
            block(zoomState)
        }
    }

    @Test
    fun `ctrl + wheel zooms around the pointer`() = runScrollTest { zoomState ->
        val zoomable = zoomState.zoomable
        val initial = zoomable.transform.scaleX
        onNodeWithTag("image").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("image").performMouseInput {
            moveTo(center)
            scroll(-3f)
        }
        onNodeWithTag("image").performKeyInput { keyUp(Key.CtrlLeft) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.scaleX > initial + 0.01f }
        assertTrue(zoomable.transform.scaleX > initial, "scale ${zoomable.transform.scaleX} should exceed $initial")
    }

    @Test
    fun `plain wheel pans when zoomed in and does nothing at fit`() = runScrollTest { zoomState ->
        val zoomable = zoomState.zoomable
        val fitOffset = zoomable.transform.offset
        onNodeWithTag("image").performMouseInput {
            moveTo(center)
            scroll(3f)
        }
        waitForIdle()
        assertEquals(fitOffset, zoomable.transform.offset)

        onNodeWithTag("image").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("image").performMouseInput { scroll(-6f) }
        onNodeWithTag("image").performKeyInput { keyUp(Key.CtrlLeft) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.scaleX > zoomable.minScale + 0.01f }
        val zoomedOffset = zoomable.transform.offset
        onNodeWithTag("image").performMouseInput { scroll(3f) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.offset != zoomedOffset }
    }
}
