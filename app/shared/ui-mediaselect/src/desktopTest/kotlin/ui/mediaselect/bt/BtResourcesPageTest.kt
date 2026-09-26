/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import me.him188.ani.app.domain.media.SOURCE_ACG
import me.him188.ani.app.domain.media.SOURCE_DMHY
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_empty
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 夹具: [TestMediaList] 里 `acg.rip.3` 是生肉 (无字幕), 在预览 selector 下被排除, 其余 4 条在主列表.
 */
@OptIn(TestOnly::class)
class BtResourcesPageTest {
    private val watching = WatchingEpisode("01", "测试")

    private fun AniComposeUiTest.setPage(
        width: Int = 400,
        height: Int = 800,
        onClickItem: (Media) -> Unit = {},
        onState: (MediaSelectorState) -> Unit = {},
        mediaList: List<Media> = TestMediaList,
        sourceResults: MediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
    ) {
        setContent {
            ProvideCompositionLocalsForPreview {
                val state = rememberTestMediaSelectorState(mediaList)
                onState(state)
                Box(Modifier.size(width.dp, height.dp)) {
                    BtResourcesPage(
                        state = state,
                        sourceResults = sourceResults,
                        watching = watching,
                        fetchRequest = TestMediaFetchRequest,
                        onFetchRequestChange = {},
                        onClickItem = onClickItem,
                        onRestartSource = {},
                        timeZone = TimeZone.UTC,
                    )
                }
            }
        }
    }

    private val rawMediaId = "$SOURCE_ACG.3"

    @Test
    fun `compact list shows included rows and hides excluded until revealed`() = runAniComposeUiTest {
        setPage()
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.1")).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.TABLE).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.row(rawMediaId)).assertDoesNotExist()

        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).assertExists().performClick()

        onNodeWithTag(BtResourcesPageTestTags.SHOW_EXCLUDED).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.row(rawMediaId)).assertExists()
    }

    @Test
    fun `episode chip toggles filter state`() = runAniComposeUiTest {
        lateinit var state: MediaSelectorState
        setPage(onState = { state = it })
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.EPISODE_CHIP).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.EPISODE_CHIP).assertIsSelected()
        assertTrue(state.btFilterState.episodeFilterEnabled.value)

        onNodeWithTag(BtResourcesPageTestTags.EPISODE_CHIP).performClick()

        runOnIdle { assertFalse(state.btFilterState.episodeFilterEnabled.value) }
        onNodeWithTag(BtResourcesPageTestTags.EPISODE_CHIP).assertIsNotSelected()
    }

    @Test
    fun `source sheet narrows main list to the chosen source`() = runAniComposeUiTest {
        lateinit var state: MediaSelectorState
        setPage(onState = { state = it })
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_ACG.1")).isDisplayed() }

        onNodeWithTag(BtResourcesPageTestTags.SOURCE_CHIP).performClick()
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.SOURCE_SHEET).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.sourceItem(SOURCE_DMHY)).performClick()

        runOnIdle { assertEquals(SOURCE_DMHY, state.btFilterState.sourceFilter.value) }
        waitUntil { onAllNodesWithTag(BtResourcesPageTestTags.row("$SOURCE_ACG.1")).fetchSemanticsNodes().isEmpty() }
        onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.1")).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.2")).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_ACG.1")).assertDoesNotExist()
    }

    @Test
    fun `clicking a row reports the media`() = runAniComposeUiTest {
        var clicked: Media? = null
        setPage(onClickItem = { clicked = it })
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.1")).isDisplayed() }

        onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.1")).performClick()

        runOnIdle { assertEquals("$SOURCE_DMHY.1", clicked?.mediaId) }
    }

    @Test
    fun `wide container uses the table`() = runAniComposeUiTest {
        setPage(width = 960, height = 700)
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.row("$SOURCE_DMHY.1")).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.TABLE).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST).assertDoesNotExist()
        onNodeWithTag(BtResourcesPageTestTags.FILTER_CHIP).assertDoesNotExist()
    }

    @Test
    fun `empty list shows loading while a bt source is still querying`() = runAniComposeUiTest {
        val emptyText = runBlocking { getString(Lang.media_selector_bt_empty) }
        // 夹具里 Mikan 仍是 Working
        setPage(mediaList = emptyList())
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.COMPACT_LIST).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.LOADING).assertExists()
        onNodeWithText(emptyText).assertDoesNotExist()
    }

    @Test
    fun `empty list shows the empty state once every bt source finished`() = runAniComposeUiTest {
        val emptyText = runBlocking { getString(Lang.media_selector_bt_empty) }
        val finished = MediaSourceResultListPresentation(
            TestMediaSourceResultListPresentation.list.map { source ->
                if (source.isWorking) source.copy(state = MediaSourceFetchState.Succeed(1)) else source
            },
        )
        setPage(mediaList = emptyList(), sourceResults = finished)
        waitUntil { onAllNodesWithText(emptyText).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(BtResourcesPageTestTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun `table shows loading while a bt source is still querying`() = runAniComposeUiTest {
        val emptyText = runBlocking { getString(Lang.media_selector_bt_empty) }
        setPage(width = 960, height = 700, mediaList = emptyList())
        waitUntil { onNodeWithTag(BtResourcesPageTestTags.TABLE).isDisplayed() }
        onNodeWithTag(BtResourcesPageTestTags.LOADING).assertExists()
        onNodeWithText(emptyText).assertDoesNotExist()
    }
}
