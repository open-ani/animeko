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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.NoOpPlaybackSpeedController
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaProgressIndicatorTextUiTest {
    @Test
    fun `switching back to one times speed restores compact layout width`() = runAniComposeUiTest {
        val progressState = PlayerProgressSliderState(
            currentPositionMillis = { 0L },
            totalDurationMillis = { 100_000L },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = {},
        )
        var layoutWidth = 0
        lateinit var playbackSpeedState: PlaybackSpeedControllerState

        setContent {
            val scope = rememberCoroutineScope()
            playbackSpeedState = remember {
                PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
            }
            MediaProgressIndicatorText(
                progressState,
                modifier = Modifier.onSizeChanged { layoutWidth = it.width },
                playbackSpeedState = playbackSpeedState,
            )
        }

        var compactWidth = 0
        runOnIdle { compactWidth = layoutWidth }

        runOnIdle { playbackSpeedState.previewSpeed(2f) }
        runOnIdle {
            assertTrue(layoutWidth > compactWidth, "remaining time mode should expand the layout")
        }

        runOnIdle { playbackSpeedState.previewSpeed(1f) }
        runOnIdle {
            assertEquals(compactWidth, layoutWidth)
        }
    }

    @Test
    fun `preview started before player initialization observes initialized duration and speed`() = runAniComposeUiTest {
        val durationMillis = mutableLongStateOf(0L)
        val state = PlayerProgressSliderState(
            currentPositionMillis = { 0L },
            totalDurationMillis = { durationMillis.longValue },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = {},
        )
        lateinit var playbackSpeedState: PlaybackSpeedControllerState

        setContent {
            val scope = rememberCoroutineScope()
            playbackSpeedState = remember {
                PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
            }
            MediaProgressIndicatorText(state, playbackSpeedState = playbackSpeedState)
        }

        runOnIdle {
            state.previewPositionRatio(0.25f)
            playbackSpeedState.previewSpeed(2f)
        }
        onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT)
            .assertTextEquals("00:00 / 00:00")

        runOnIdle {
            durationMillis.longValue = 10 * 60 * 1000L
        }

        onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT)
            .assertTextEquals("02:30 / 10:00 (-03:45)")
    }
}
