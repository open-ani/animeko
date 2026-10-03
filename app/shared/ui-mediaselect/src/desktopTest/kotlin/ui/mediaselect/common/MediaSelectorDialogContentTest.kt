/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestManualBrowseState
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPageTestTags
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePageTestTags
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 容器内容: 紧凑时单行顶栏 (X + 模式名, 没有模式 chip), 否则 TopAppBar 带模式 chip; X 交给宿主回侧边栏, 选中后交给宿主全关.
 */
@OptIn(TestOnly::class)
class MediaSelectorDialogContentTest {
    private var closed = 0
    private var played = 0
    private lateinit var selectorState: MediaSelectorState

    private fun AniComposeUiTest.render(mode: MediaSelectorMode, compact: Boolean) {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(844.dp, 390.dp)) {
                    selectorState = rememberTestMediaSelectorState()
                    MediaSelectorDialogContent(
                        mode,
                        compact,
                        onModeChange = {},
                        onClose = { closed++ },
                        onPlayed = { played++ },
                        mediaSelectorState = selectorState,
                        sourceResults = TestMediaSourceResultListPresentation,
                        watching = WatchingEpisode("25", "OVA"),
                        fetchRequest = TestMediaFetchRequest,
                        onFetchRequestChange = {},
                        onRestartSource = {},
                        manualBrowseState = rememberTestManualBrowseState(),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `compact manual shows an inline close button without the mode chip`() = runAniComposeUiTest {
        render(MediaSelectorMode.MANUAL, compact = true)
        awaitTag(ManualBrowsePageTestTags.ROOT)
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertDoesNotExist()

        onNodeWithTag(MediaSelectorDialogTestTags.CLOSE).assertIsDisplayed().performClick()
        assertEquals(1, closed)
        assertEquals(0, played)
    }

    @Test
    fun `non-compact bt shows the top bar with the mode chip`() = runAniComposeUiTest {
        render(MediaSelectorMode.BT, compact = false)
        awaitTag(BtResourcesPageTestTags.ROOT)
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertIsDisplayed()
        onNodeWithTag(MediaSelectorDialogTestTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `picking a bt resource selects it and closes everything`() = runAniComposeUiTest {
        render(MediaSelectorMode.BT, compact = true)
        waitUntil(timeoutMillis = 10_000) { selectorState.btPresentationFlow.value.included.any { !it.isSelected } }
        val target = selectorState.btPresentationFlow.value.included.first { !it.isSelected }.id
        awaitTag(BtResourcesPageTestTags.row(target))
        onNodeWithTag(BtResourcesPageTestTags.row(target)).performClick()

        assertEquals(1, played)
        assertEquals(0, closed)
        waitUntil(timeoutMillis = 10_000) {
            selectorState.btPresentationFlow.value.included.any { it.id == target && it.isSelected }
        }
    }

    @Test
    fun `auto mode draws nothing`() = runAniComposeUiTest {
        render(MediaSelectorMode.AUTO, compact = false)
        waitForIdle()
        onNodeWithTag(MediaSelectorDialogTestTags.CLOSE).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.ROOT).assertDoesNotExist()
        onNodeWithTag(ManualBrowsePageTestTags.ROOT).assertDoesNotExist()
    }
}
