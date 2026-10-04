/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 编辑器把配置分为列表规则与自动匹配两层: 自动匹配区默认收起, 展开后可以改 [SelectorMediaSourceArguments.tier]
 * 与 `autoMatch.enabled`.
 */
@OptIn(TestOnly::class)
class SelectorConfigPaneTest {
    private fun setUp(
        initial: SelectorMediaSourceArguments = SelectorMediaSourceArguments.Default,
    ): Pair<androidx.compose.runtime.MutableState<SelectorMediaSourceArguments?>, SelectorConfigState> {
        val container = mutableStateOf<SelectorMediaSourceArguments?>(initial)
        val state = SelectorConfigState(
            SaveableStorage(
                containerState = container,
                onSave = { container.value = it },
                isSavingFlow = MutableStateFlow(false),
            ),
            allowEditState = stateOf(true),
        )
        return container to state
    }

    @Test
    fun `auto match section is collapsed by default and expands on header click`() = runAniComposeUiTest {
        val (_, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                SelectorConfigurationPane(state)
            }
        }
        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_ENABLED).assertDoesNotExist()

        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_HEADER).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_ENABLED).performScrollTo().assertExists()
        onNodeWithTag(SelectorConfigPaneTestTags.TIER).performScrollTo().assertExists()
    }

    @Test
    fun `toggling the enabled switch writes autoMatch enabled`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                SelectorConfigurationPane(state)
            }
        }
        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_HEADER).performScrollTo().performClick()
        waitForIdle()
        assertTrue(container.value!!.searchConfig.autoMatch.enabled)

        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_ENABLED).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_ENABLED).assertIsOff()
        assertFalse(container.value!!.searchConfig.autoMatch.enabled)
    }

    @Test
    fun `editing tier writes the arguments tier`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                SelectorConfigurationPane(state)
            }
        }
        onNodeWithTag(SelectorConfigPaneTestTags.AUTO_MATCH_HEADER).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(SelectorConfigPaneTestTags.TIER).performScrollTo().performTextClearance()
        onNodeWithTag(SelectorConfigPaneTestTags.TIER).performTextInput("0")
        waitForIdle()

        assertEquals(MediaSourceTier(0u), container.value!!.tier)
    }
}
