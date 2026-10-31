/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(InternalMediampApi::class)

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.SubtitlePresentation
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleSwitcherTest {
    @Test
    fun `video subtitle menu selects a track and can turn subtitles off`() = runAniComposeUiTest {
        val options = listOf(
            SubtitlePresentation(SubtitleTrack("0", "0", "chi", emptyList()), "Chinese Simplified"),
            SubtitlePresentation(SubtitleTrack("1", "1", "eng", emptyList()), "English"),
        )
        var selected by mutableStateOf<SubtitlePresentation?>(null)
        var offText = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                offText = stringResource(Lang.video_player_off)
                PlayerControllerDefaults.SubtitleSwitcher(selected, { selected = it }, { options })
            }
        }
        onNodeWithTag("textButton").performClick()
        onNodeWithText("Chinese Simplified").performClick()
        runOnIdle { assertEquals("0", selected?.subtitleTrack?.id) }
        onNodeWithTag("textButton").performClick()
        onNodeWithText("English").performClick()
        runOnIdle { assertEquals("1", selected?.subtitleTrack?.id) }
        onNodeWithTag("textButton").performClick()
        onNodeWithText(offText).performClick()
        runOnIdle { assertNull(selected) }
    }
}
