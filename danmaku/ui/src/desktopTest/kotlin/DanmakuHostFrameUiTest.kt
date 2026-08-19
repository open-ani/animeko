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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 逐帧验证弹幕运动的端到端性质 (通过 Compose UI test 的确定性帧时钟):
 *
 * 1. 匀速: 每一帧的位移完全一致;
 * 2. 配置变更 (字号) 触发重新放置时, 弹幕保留原有锚点与速度系数, 位置零跳变;
 * 3. 连续播放时的 repopulate (弹幕列表刷新) 保留在屏弹幕原对象不动;
 * 4. 快进时的 repopulate 清屏重灌.
 */
class DanmakuHostFrameUiTest {
    private fun danmaku(
        id: String,
        playTimeMillis: Long,
        text: String = "danmaku $id",
    ) = DanmakuPresentation(
        DanmakuInfo(
            id, DanmakuServiceId("test"), "sender",
            DanmakuContent(playTimeMillis, 0xffffff, text, DanmakuLocation.NORMAL),
        ),
        isSelf = false,
    )

    private fun DanmakuHostState.allFloating(): List<FloatingDanmaku<StyledDanmaku>> {
        val result = mutableListOf<FloatingDanmaku<StyledDanmaku>>()
        forEachFloatingDanmaku { result.add(it) }
        return result
    }

    private fun DanmakuHostState.floatingById(id: String): FloatingDanmaku<StyledDanmaku>? =
        allFloating().firstOrNull { it.danmaku.presentation.danmaku.id == id }

    @Test
    fun `danmaku moves uniformly and re-placement does not jump`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val config = mutableStateOf(
            DanmakuConfig(
                displayArea = 1.0f,
                speed = 20f, // 慢速, 保证被跟踪的弹幕在整个测试期间可见
            ),
        )
        val state = DanmakuHostState(config)
        val scope = CoroutineScope(Dispatchers.Main + Job())

        try {
            setContent {
                DanmakuHost(state, Modifier.size(500.dp, 300.dp))
            }
            mainClock.advanceTimeByFrame()
            waitUntil("tracks created", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                state.floatingTrack.isNotEmpty()
            }

            var placed: Boolean? = null
            scope.launch { placed = state.trySend(danmaku("a", 5_000)) }
            waitUntil("danmaku a placed", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                placed == true && state.floatingById("a") != null
            }

            val tracked = assertNotNull(state.floatingById("a"))
            val anchorBefore = tracked.placeFrameTimeNanos
            val multiplierBefore = tracked.speedMultiplier

            fun currentX(): Float {
                val danmaku = assertNotNull(state.floatingById("a"), "danmaku a must stay visible")
                return state.hostWidth - danmaku.distanceX
            }

            // 1) 采样 60 帧: 每帧位移完全一致 (测试帧时钟为固定 16ms, 平滑器透传)
            var lastX = currentX()
            mainClock.advanceTimeByFrame()
            val expectedStep = lastX - currentX() // 向左为正
            assertTrue(expectedStep > 0f, "danmaku must move left, got step $expectedStep")
            lastX = currentX()
            repeat(60) {
                mainClock.advanceTimeByFrame()
                val x = currentX()
                val step = lastX - x
                assertTrue(
                    abs(step - expectedStep) < 0.05f,
                    "frame $it: non-uniform step $step (expected $expectedStep)",
                )
                lastX = x
            }

            // 2) 字号变更 -> repopulatePresentDanmaku: 锚点与速度系数保留, 位置零跳变
            val xBefore = currentX()
            config.value = config.value.copy(
                style = config.value.style.copy(fontSize = 27.sp),
            )
            waitUntil("danmaku re-placed after font change", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                state.floatingById("a").let { it != null && it !== tracked }
            }
            val replaced = assertNotNull(state.floatingById("a"))
            assertEquals(anchorBefore, replaced.placeFrameTimeNanos, "anchor must be preserved")
            assertEquals(multiplierBefore, replaced.speedMultiplier, "speed multiplier must not be re-rolled")
            // waitUntil 推进了若干帧, 位置必须仍在同一条匀速直线上
            val framesAdvanced = ((xBefore - currentX()) / expectedStep)
            assertTrue(
                abs(framesAdvanced - framesAdvanced.toInt()) < 0.05f || expectedStep < 0.01f,
                "position deviated from the uniform line across re-placement",
            )

            // 3) 连续播放时 repopulate: 在屏弹幕保留同一对象, 新弹幕加入
            var repopulated = false
            scope.launch {
                state.repopulate(
                    listOf(danmaku("a", 5_000), danmaku("b", 6_000)),
                    currentPlayMillis = 5_500,
                )
                repopulated = true
            }
            waitUntil("repopulate (continuous) done", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                repopulated
            }
            assertSame(
                replaced, state.floatingById("a"),
                "on-screen danmaku must be kept untouched by a continuous repopulate",
            )

            // 4) 快进 (不连续) repopulate: 清屏重灌
            var seeked = false
            scope.launch {
                state.repopulate(
                    listOf(danmaku("c", 60_000)),
                    currentPlayMillis = 60_000,
                )
                seeked = true
            }
            waitUntil("repopulate (seek) done", timeoutMillis = 10_000) {
                mainClock.advanceTimeByFrame()
                seeked
            }
            assertTrue(
                state.floatingById("a") == null,
                "danmaku from before the seek should be gone",
            )
            assertNotNull(state.floatingById("c"), "danmaku at the seek target should be visible")
        } finally {
            scope.cancel()
        }
    }
}
