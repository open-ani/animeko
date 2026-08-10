/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 逐帧验证弹幕运动的端到端性质 (通过 Compose UI test 的确定性帧时钟):
 *
 * 1. 匀速: 每一帧的屏幕位置都严格落在匀速直线上;
 * 2. 列表刷新 (重新编译) 时, 在屏弹幕位置零跳变;
 * 3. seek 时可见窗口正确重建.
 */
class DanmakuHostFrameUiTest {
    private fun danmaku(
        id: String,
        timeMillis: Long,
        text: String = "danmaku $id",
    ) = DanmakuPresentation(
        DanmakuInfo(
            id, DanmakuServiceId("test"), "sender",
            DanmakuContent(timeMillis, 0xffffff, text, DanmakuLocation.NORMAL),
        ),
        isSelf = false,
    )

    @Test
    fun `danmaku moves uniformly, survives list refresh without jump, and seek rebuilds window`() =
        runAniComposeUiTest {
            mainClock.autoAdvance = false
            val config = mutableStateOf(
                DanmakuConfig(
                    displayArea = 1.0f,
                    speed = 20f, // 慢速, 保证被跟踪的弹幕在整个测试期间可见
                ),
            )
            val state = DanmakuHostState(config)

            setContent {
                DanmakuHost(state, Modifier.size(500.dp, 300.dp))
            }
            mainClock.advanceTimeByFrame()

            state.setDanmakuList(listOf(danmaku("a", 5_000), danmaku("b", 8_000)))
            state.onPositionReport(5_000)
            waitUntil("layout compiled and danmaku a visible", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                state.visibleFloating.any { it.placed.presentation.danmaku.id == "a" }
            }

            fun trackedDanmaku() =
                state.visibleFloating.first { it.placed.presentation.danmaku.id == "a" }.placed

            fun currentX(): Float =
                state.hostWidth - trackedDanmaku().distanceXAt(state.currentVideoTimeMillis)

            // 1) 采样 60 帧: 每帧位移严格均匀 (测试帧时钟为固定 16ms)
            val speedPxPerSecond = trackedDanmaku().speedPxPerVideoSecond
            val expectedPerFrame = speedPxPerSecond * 16f / 1000f
            var lastX = currentX()
            repeat(60) {
                mainClock.advanceTimeByFrame()
                val x = currentX()
                val delta = lastX - x // 向左运动
                assertEquals(
                    expectedPerFrame, delta, absoluteTolerance = 0.02f,
                    "frame $it: non-uniform step $delta (expected $expectedPerFrame)",
                )
                lastX = x
            }

            // 2) 列表刷新触发重新编译: 在屏弹幕位置必须仍在同一条匀速直线上 (零跳变)
            val oldLayout = state.compiledLayout
            val xBefore = currentX()
            val timeBefore = state.currentVideoTimeMillis
            state.setDanmakuList(
                listOf(
                    danmaku("a", 5_000), danmaku("b", 8_000),
                    danmaku("c", 12_000), danmaku("d", 60_000),
                ),
            )
            waitUntil("layout recompiled", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                state.compiledLayout !== oldLayout
            }
            mainClock.advanceTimeByFrame()

            val timeAfter = state.currentVideoTimeMillis
            val xAfter = currentX()
            val expectedX = xBefore - (timeAfter - timeBefore) / 1000f * speedPxPerSecond
            assertTrue(
                abs(xAfter - expectedX) < 0.5f,
                "danmaku jumped by ${xAfter - expectedX}px across recompilation",
            )
            // 速度也不能变 (没有重掷随机速度)
            assertEquals(speedPxPerSecond, trackedDanmaku().speedPxPerVideoSecond)

            // 3) seek: 可见窗口正确重建
            state.onPositionReport(60_000)
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertTrue(
                state.visibleFloating.none { it.placed.presentation.danmaku.id == "a" },
                "danmaku from before the seek should be gone",
            )
            assertTrue(
                state.visibleFloating.any { it.placed.presentation.danmaku.id == "d" },
                "danmaku at the seek target should be visible",
            )
        }
}
