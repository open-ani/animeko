/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DanmakuRendererTest {
    @Test
    fun `bounded opacity preserves overlapping text and bitmap margins`() = runAniComposeUiTest {
        val config = mutableStateOf(DanmakuConfig())
        val state = DanmakuHostState(config)
        var text: StyledDanmaku? = null
        setContent {
            val measurer = rememberTextMeasurer()
            SideEffect {
                text = dummyDanmaku(
                    measurer, TextStyle.Default,
                    DanmakuStyle.Default.copy(shadow = Shadow(blurRadius = 4f)),
                    "测试 overlap の text",
                )
            }
        }
        waitForIdle()
        val styled = assertNotNull(text)
        val renderer = DanmakuRenderer()
        val height = 160
        val width = 320

        fun add(y: Float) {
            val track = FixedDanmakuTrack<StyledDanmaku>(
                0, false, mutableLongStateOf(0), mutableIntStateOf(30),
                mutableIntStateOf(height), mutableLongStateOf(10_000),
            )
            track.place(styled, 0).y = y
            state.topTrack.add(track)
        }

        fun verify() {
            for (alpha in listOf(0f, 0.3f, 0.5f, 0.8f, 1f)) {
                config.value = config.value.copy(style = config.value.style.copy(alpha = alpha))
                val expected = render(width, height) {
                    val canvas = drawContext.canvas
                    if (alpha != 1f) {
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = alpha })
                    }
                    state.forEachFixedDanmaku {
                        if (!it.y.isNaN()) with(it.danmaku) {
                            draw((width - danmakuWidth) / 2f, it.y)
                        }
                    }
                    if (alpha != 1f) canvas.restore()
                }
                val actual = render(width, height) { with(renderer) { draw(state, width) } }
                val firstDifference = expected.indices.firstOrNull { expected[it] != actual[it] }
                assertEquals(null, firstDifference, "alpha=$alpha first mismatched pixel")
            }
        }

        verify() // Empty occupancy.
        add(Float.NaN)
        verify() // Uninitialized positions must not be included.
        add(-3.25f)
        add(7.5f)
        verify() // Partial top clipping and overlapping translucent text.
        add(height - styled.danmakuHeight - 0.25f)
        verify() // Bottom text expands the layer, including its shadow margin.
        state.topTrack.clear()
        add(12.25f)
        verify() // Bounds must shrink after bottom text disappears.
    }

    private fun render(width: Int, height: Int, block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()),
        ) {
            drawRect(Color(0xFF204060))
            block()
        }
        return IntArray(width * height).also { image.readPixels(it) }
    }
}
