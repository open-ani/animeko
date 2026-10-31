/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSettingsDialog
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubtitleSettingsDialogTest {
    @Test
    fun `subtitle size can increase reset and close with a visible multiline preview`() = runAniComposeUiTest {
        var scale by mutableFloatStateOf(1f)
        var visible by mutableStateOf(true)
        var close = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                close = stringResource(Lang.subject_episode_close)
                if (visible) SubtitleSettingsDialog(scale, { scale = it }, { visible = false })
            }
        }
        onNodeWithTag("SubtitleFontScale").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        onNodeWithTag("SubtitleSizePreview").assertIsDisplayed()
        onNodeWithText(close).performClick()
        runOnIdle { assertEquals(2f, scale); visible = true }
        onNodeWithTag("ResetSubtitleSize").performClick()
        runOnIdle { assertEquals(1f, scale) }
        onNodeWithText(close).performClick()
        runOnIdle { assertFalse(visible) }
    }
}
