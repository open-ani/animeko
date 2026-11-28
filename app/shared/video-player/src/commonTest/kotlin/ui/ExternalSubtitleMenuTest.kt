/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.videoplayer.subtitle.ExternalSubtitleController
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer

class ExternalSubtitleMenuTest {
    @Test
    fun `imported local and online subtitles remain selectable after turning them off`() = runAniComposeUiTest {
        lateinit var controller: ExternalSubtitleController
        var off = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val player = remember { TestMediampPlayer(scope.coroutineContext) }
                controller = remember { ExternalSubtitleController(player, scope) }
                off = stringResource(Lang.video_player_off)
                LaunchedEffect(Unit) {
                    val media = UriMediaData("https://test/video.mp4")
                    player.setMediaData(media)
                    val bytes = "1\n00:00:01,000 --> 00:00:03,000\nCaption".encodeToByteArray()
                    controller.select("local.srt", bytes, media)
                    controller.select("online.srt", bytes, media)
                }
                PlayerControllerDefaults.SubtitleSwitcher(controller.tracks)
            }
        }
        waitUntil { controller.selected.value?.name == "online.srt" }
        waitUntil { onAllNodesWithTag("textButton").fetchSemanticsNodes().size == 1 }
        onNodeWithTag("textButton").performClick()
        onNodeWithText(off).performClick()
        runOnIdle { assertNull(controller.selected.value) }
        onNodeWithTag("textButton").performClick()
        onNodeWithText("local.srt").performClick()
        runOnIdle { assertEquals("local.srt", controller.selected.value?.name) }
        onNodeWithTag("textButton").performClick()
        onNodeWithText(off).performClick()
        onNodeWithTag("textButton").performClick()
        onNodeWithText("online.srt").performClick()
        runOnIdle { assertEquals("online.srt", controller.selected.value?.name) }
    }
}
