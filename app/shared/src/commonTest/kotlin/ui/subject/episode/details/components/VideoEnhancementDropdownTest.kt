/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.ui.lang.video_player_performance
import me.him188.ani.app.ui.lang.video_player_quality
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementController
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VideoEnhancementDropdownTest {
    @Test
    fun `unbounded popup can open select every mode and reopen`() = verifyModes(
        // Model the unbounded measurement supplied by the iOS popup host.
        Modifier.wrapContentSize(unbounded = true),
    )

    @Test
    fun `short popup can scroll to every mode`() = verifyModes(Modifier.heightIn(max = 120.dp))

    private fun verifyModes(modifier: Modifier) = runAniComposeUiTest {
        val controller = TestVideoEnhancementController()
        var expanded by mutableStateOf(false)
        lateinit var performanceText: String
        lateinit var qualityText: String
        lateinit var offText: String
        setContent {
            performanceText = stringResource(Lang.video_player_performance)
            qualityText = stringResource(Lang.video_player_quality)
            offText = stringResource(Lang.video_player_off)
            MaterialTheme {
                Box {
                    Button(onClick = { expanded = true }) { Text("Open enhancement") }
                    VideoEnhancementDropdown(
                        controller,
                        expanded,
                        onDismissRequest = { expanded = false },
                        modifier = modifier,
                    )
                }
            }
        }

        onNodeWithText("Open enhancement").performClick()
        onNodeWithText(offText).assertIsNotEnabled()
        for ((mode, text) in listOf(
            VideoEnhancementMode.PERFORMANCE to performanceText,
            VideoEnhancementMode.QUALITY to qualityText,
            VideoEnhancementMode.OFF to offText,
        )) {
            onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
            runOnIdle {
                assertEquals(mode, controller.mode.value)
                assertFalse(expanded)
            }
            onNodeWithText("Open enhancement").performClick()
            onNodeWithText(text).assertIsNotEnabled()
        }
    }

    private class TestVideoEnhancementController : VideoEnhancementController {
        override val mode = MutableStateFlow(VideoEnhancementMode.OFF)

        override fun setMode(mode: VideoEnhancementMode) {
            this.mode.value = mode
        }

        override fun setViewportSize(width: Int, height: Int) = Unit

        override fun close() = Unit
    }
}
