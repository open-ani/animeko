/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import org.openani.mediamp.metadata.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerProgressSliderStateTest {

    @Test
    fun `test basic seek operation`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = { position ->
                positionState = position
            },
        )

        // 拖动到 50% 位置
        state.previewPositionRatio(0.5f)
        state.finishPreview()
        takeSnapshot()
        
        // 验证显示位置是 50%
        assertEquals(0.5f, state.displayPositionRatio)
        // 验证 isSeeking 状态
        assertTrue(state.isPreviewing)

        // 模拟播放器位置更新到目标位置
        positionState = 50_000L
        state.checkSeekingComplete()
        takeSnapshot()
        
        // 验证 isSeeking 状态结束
        assertFalse(state.isPreviewing)
        assertEquals(0.5f, state.displayPositionRatio)
    }

    @Test
    fun `test rapid consecutive drag operations`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)
        var lastSeekPosition: Long = 0L

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = { position ->
                lastSeekPosition = position
                // 模拟播放器延迟更新
            },
        )

        // 第一次拖动到 A 点 (30%)
        state.previewPositionRatio(0.3f)
        state.finishPreview()
        takeSnapshot()
        assertEquals(0.3f, state.displayPositionRatio)
        assertTrue(state.isPreviewing)
        assertEquals(30_000L, lastSeekPosition)

        // 快速第二次拖动到 B 点 (70%)
        state.previewPositionRatio(0.7f)
        state.finishPreview()
        takeSnapshot()
        
        // 验证显示位置是 B 点，不是 A 点
        assertEquals(0.7f, state.displayPositionRatio)
        assertTrue(state.isPreviewing)
        assertEquals(70_000L, lastSeekPosition)

        // 模拟播放器位置更新到 A 点（中间位置）
        positionState = 30_000L
        state.checkSeekingComplete()
        takeSnapshot()
        
        // 验证 isSeeking 状态仍然为 true（因为还没到最后一次的目标位置）
        assertTrue(state.isPreviewing)
        // 验证显示位置仍然是 B 点
        assertEquals(0.7f, state.displayPositionRatio)

        // 模拟播放器位置更新到 B 点
        positionState = 70_000L
        state.checkSeekingComplete()
        takeSnapshot()
        
        // 验证 isSeeking 状态结束
        assertFalse(state.isPreviewing)
        // 验证显示位置是 B 点
        assertEquals(0.7f, state.displayPositionRatio)
    }

    @Test
    fun `test seek completion detection`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = { position ->
                positionState = position
            },
        )

        // 拖动到 60% 位置
        state.previewPositionRatio(0.6f)
        state.finishPreview()
        takeSnapshot()

        // 模拟播放器位置接近目标位置（误差 < 500ms）
        positionState = 60_200L
        state.checkSeekingComplete()
        takeSnapshot()
        
        // 验证 isSeeking 状态结束
        assertFalse(state.isPreviewing)

        // 再次拖动到 80% 位置
        state.previewPositionRatio(0.8f)
        state.finishPreview()
        takeSnapshot()
        assertTrue(state.isPreviewing)

        // 模拟播放器位置超过目标位置
        positionState = 80_500L
        state.checkSeekingComplete()
        takeSnapshot()
        
        // 验证 isSeeking 状态结束
        assertFalse(state.isPreviewing)
    }

    @Test
    fun `test no seeking when preview ratio is NaN`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)
        var seekCalled = false

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = {
                seekCalled = true
            },
        )

        // 调用 finishPreview 但没有设置预览位置
        state.finishPreview()
        takeSnapshot()
        
        // 验证 seek 没有被调用
        assertFalse(seekCalled)
        assertFalse(state.isPreviewing)
    }
}
