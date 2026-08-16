/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MpvOptionsItemTest {
    @Test
    fun `confirming the dialog saves all entered lines`() = runAniComposeUiTest {
        lateinit var state: SettingsState<PlayerKernelConfig>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(PlayerKernelConfig.Default)
                    PlayerGroupPlatform(rememberTestSettingsState(VideoScaffoldConfig.Default), state)
                }
            }
        }

        onNodeWithTag(MpvOptionsItemTestTags.ITEM).performClick()
        waitForIdle()

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).performTextInput("hwdec=auto\nprofile=fast")
        onNodeWithText("确认").performClick()

        waitUntil { state.value.mpvOptions == listOf("hwdec=auto", "profile=fast") }
    }

    @Test
    fun `dialog shows the saved options and does not write before confirming`() = runAniComposeUiTest {
        lateinit var state: SettingsState<PlayerKernelConfig>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(
                        PlayerKernelConfig.Default.copy(mpvOptions = listOf("hwdec=auto")),
                    )
                    PlayerGroupPlatform(rememberTestSettingsState(VideoScaffoldConfig.Default), state)
                }
            }
        }

        onNodeWithTag(MpvOptionsItemTestTags.ITEM).performClick()
        waitForIdle()

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).assertTextEquals("hwdec=auto")

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).performTextInput("\nprofile=fast")
        waitForIdle()

        assertEquals(listOf("hwdec=auto"), state.value.mpvOptions)
    }
}
