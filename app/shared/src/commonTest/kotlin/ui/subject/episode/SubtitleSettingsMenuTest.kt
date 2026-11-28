/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.videoplayer.ui.rememberVideoControllerState
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SubtitleSettingsMenuTest {
    @Test
    fun `three dot menu opens subtitle settings and retains the chosen size when reopened`() = runAniComposeUiTest {
        var scale by mutableFloatStateOf(1f)
        var close = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val player = remember { TestMediampPlayer(scope.coroutineContext) }
                val controller = rememberVideoControllerState()
                val sheets = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
                close = stringResource(Lang.subject_episode_close)
                Row {
                    EpisodeVideoTopBarActions(
                        playerState = player,
                        subtitleFontScale = scale,
                        onSubtitleFontScaleChange = { scale = it },
                        expanded = true,
                        opEdSkipDuration = 85.seconds,
                        onClickSkipOpEd = {},
                        sheetsController = sheets,
                        shareData = MediaShareData(null, null),
                        onClickCache = {},
                        onClickWatchTogether = {},
                        playerControllerState = controller,
                        videoEnhancement = null,
                        videoAspectRatioControllerState = null,
                        sidebarVisible = false,
                        onToggleSidebar = {},
                        playerStatsVisible = false,
                        onTogglePlayerStats = {},
                    )
                }
            }
        }
        onNodeWithTag("PlayerMoreOptions").performClick()
        onNodeWithTag("SubtitleSettingsMenuItem").performClick()
        onNodeWithTag("SubtitleSizePreview").assertIsDisplayed()
        onNodeWithTag("SubtitleFontScale").performSemanticsAction(SemanticsActions.SetProgress) { it(1.75f) }
        onNodeWithText(close).performClick()
        runOnIdle { assertEquals(1.75f, scale) }
        onNodeWithTag("PlayerMoreOptions").performClick()
        onNodeWithTag("SubtitleSettingsMenuItem").performClick()
        onNodeWithTag("ResetSubtitleSize").performClick()
        runOnIdle { assertEquals(1f, scale) }
    }
}
