/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestManualBrowseState
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPageTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePageTestTags
import me.him188.ani.app.ui.subject.episode.TAG_MEDIA_SELECTOR_SHEET
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test

/**
 * 播放器侧边栏里切模式只换内容: 三种模式都画在同一个侧边栏里.
 * 侧边栏面板用 clickable 拦截点击, 会合并子节点语义, 所以查找都用未合并树.
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class EpisodeVideoMediaSelectorSideSheetTest {
    private fun AniComposeUiTest.render(mode: MediaSelectorMode) {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(1280.dp, 800.dp)) {
                    EpisodeVideoSideSheets.MediaSelectorSheet(
                        mediaSelectorState = rememberTestMediaSelectorState(),
                        mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
                        mode = mode,
                        onModeChange = {},
                        onDismissRequest = {},
                        onRestartSource = {},
                        watching = WatchingEpisode("25", "OVA"),
                        fetchRequest = TestMediaFetchRequest,
                        manualBrowseState = rememberTestManualBrowseState(),
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.awaitNoTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `bt mode shows the bt page in the sheet`() = runAniComposeUiTest {
        render(MediaSelectorMode.BT)
        onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET, useUnmergedTree = true).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.ROOT, useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithTag(MediaSelectorChromeTestTags.MODE_CHIP, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `manual mode shows the manual page in the sheet with a single header`() = runAniComposeUiTest {
        render(MediaSelectorMode.MANUAL)
        onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET, useUnmergedTree = true).assertExists()
        onNodeWithTag(ManualBrowsePageTestTags.ROOT, useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithTag(MediaSelectorChromeTestTags.MODE_CHIP, useUnmergedTree = true).assertCountEquals(1)

        onNodeWithTag(ManualBrowsePageTestTags.SEARCH_FIELD, useUnmergedTree = true).performImeAction()
        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithTag(ManualBrowsePageTestTags.result(0), useUnmergedTree = true).performClick()

        // 第二页由页面自己的返回栏取代侧边栏标题行, 不叠两层顶栏.
        awaitTag(ManualBrowsePageTestTags.BACK)
        awaitNoTag(MediaSelectorChromeTestTags.MODE_CHIP)

        onNodeWithTag(ManualBrowsePageTestTags.BACK, useUnmergedTree = true).performClick()
        awaitNoTag(ManualBrowsePageTestTags.BACK)
        onAllNodesWithTag(MediaSelectorChromeTestTags.MODE_CHIP, useUnmergedTree = true).assertCountEquals(1)
    }
}
