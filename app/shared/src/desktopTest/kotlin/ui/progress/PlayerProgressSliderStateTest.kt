/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.state.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerProgressSliderStateTest {

    @Test
    fun `preview and finish preview then play`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = mutableStateOf(chapters),
            onPreview = {},
            onPreviewFinished = {
                positionState = it
            },
        )

        state.previewPositionRatio(0.5f)
        state.finishPreview()
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.55f, state.displayPositionRatio)
    }

    @Test
    fun `preview and play 5s and finish preview then play`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = mutableStateOf(chapters),
            onPreview = {},
            onPreviewFinished = {
                positionState = it
            },
        )

        state.previewPositionRatio(0.5f)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        state.finishPreview()
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.55f, state.displayPositionRatio)
    }
}