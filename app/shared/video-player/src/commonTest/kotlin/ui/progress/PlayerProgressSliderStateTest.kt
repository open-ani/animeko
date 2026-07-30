/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.runtime.mutableLongStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayerProgressSliderStateTest {
    @Test
    fun `normalizes invalid player timeline values`() {
        val positionMillis = mutableLongStateOf(-1L)
        val durationMillis = mutableLongStateOf(-1L)
        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionMillis.longValue },
            totalDurationMillis = { durationMillis.longValue },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = {},
        )

        assertEquals(0L, state.currentPositionMillis)
        assertEquals(0L, state.totalDurationMillis)
        assertEquals(0f, state.displayPositionRatio)

        positionMillis.longValue = 120_000L
        durationMillis.longValue = 100_000L

        assertEquals(120_000L, state.currentPositionMillis)
        assertEquals(100_000L, state.totalDurationMillis)
        assertEquals(1f, state.displayPositionRatio)
    }

    @Test
    fun `cancel preview does not finish seek`() {
        val finishedPositions = mutableListOf<Long>()
        val state = PlayerProgressSliderState(
            currentPositionMillis = { 10_000L },
            totalDurationMillis = { 100_000L },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = finishedPositions::add,
        )

        state.previewPositionRatio(0.5f)
        state.cancelPreview()

        assertFalse(state.isPreviewing)
        assertEquals(emptyList(), finishedPositions)
        assertEquals(0.1f, state.displayPositionRatio)
    }
}
