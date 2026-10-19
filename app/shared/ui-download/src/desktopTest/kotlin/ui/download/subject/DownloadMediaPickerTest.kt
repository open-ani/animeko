/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_mode_auto
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.createTestManualBrowseState
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPageTestTags
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPageTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePageTestTags
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseTarget
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadMediaPickerTest {
    /**
     * @param onManualPick 非 null 时提供手动查找, 点选的一集交给它; null 时宿主没有手动查找.
     */
    @Composable
    private fun Picker(hasBt: Boolean, onManualPick: ((Media, ManualBrowseMemory?) -> Unit)? = null) {
        ProvideCompositionLocalsForPreview {
            val scope = rememberBackgroundScope()
            val manualBrowse = remember {
                onManualPick?.let { onPick ->
                    createTestManualBrowseState(
                        scope.backgroundScope,
                        target = ManualBrowseTarget(1, "命运石之门", EpisodeSort(1), "1"),
                        onPlay = { pick, memory -> onPick(pick.media, memory) },
                        rememberSelection = MutableStateFlow(false),
                    )
                }
            }
            DownloadMediaPickerContent(
                selectorState = rememberTestMediaSelectorState(),
                sourceResults = TestMediaSourceResultListPresentation,
                hasBt = hasBt,
                watching = WatchingEpisode("01", "Episode 1"),
                fetchRequest = TestMediaFetchRequest,
                onFetchRequestChange = {},
                onRestartSource = {},
                onSelect = {},
                manualBrowse = manualBrowse,
            )
        }
    }

    private fun modeName(mode: MediaSelectorMode) = runBlocking {
        when (mode) {
            MediaSelectorMode.AUTO -> getString(Lang.media_selector_mode_auto)
            MediaSelectorMode.BT -> getString(Lang.media_selector_mode_bt)
            MediaSelectorMode.MANUAL -> getString(Lang.media_selector_mode_manual)
        }
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `defaults to BT with BT sources and switches to auto from the menu`() = runAniComposeUiTest {
        setContent { Picker(hasBt = true) }
        onNodeWithTag(BtResourcesPageTestTags.ROOT).assertExists()
        onNodeWithTag(AutoMatchPageTestTags.ROOT).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertTextEquals(modeName(MediaSelectorMode.BT))

        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.AUTO)).performClick()

        onNodeWithTag(AutoMatchPageTestTags.ROOT).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.ROOT).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertTextEquals(modeName(MediaSelectorMode.AUTO))
    }

    @Test
    fun `defaults to auto without BT sources and offers neither BT nor manual`() = runAniComposeUiTest {
        setContent { Picker(hasBt = false) }
        onNodeWithTag(AutoMatchPageTestTags.ROOT).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.ROOT).assertDoesNotExist()
        // 宿主没有手动查找状态时没有手动查找入口, 自动页也不显示救援按钮.
        onNodeWithTag(AutoMatchPageTestTags.RESCUE_BUTTON).assertDoesNotExist()

        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.AUTO)).assertExists()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
    }

    @Test
    fun `menu offers manual search and switching keeps the title row`() = runAniComposeUiTest {
        setContent { Picker(hasBt = true, onManualPick = { _, _ -> }) }
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).performClick()

        onNodeWithTag(ManualBrowsePageTestTags.ROOT).assertExists()
        onNodeWithTag(BtResourcesPageTestTags.ROOT).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertTextEquals(modeName(MediaSelectorMode.MANUAL))
        // 下载弹窗不画「正在观看」卡片.
        onNodeWithTag(MediaSelectorChromeTestTags.WATCHING).assertDoesNotExist()
    }

    @Test
    fun `rescue button opens manual search and a picked episode goes to the host without memory`() = runAniComposeUiTest {
        val picks = mutableListOf<Pair<Media, ManualBrowseMemory?>>()
        setContent { Picker(hasBt = false, onManualPick = { media, memory -> picks += media to memory }) }
        onNodeWithTag(AutoMatchPageTestTags.RESCUE_BUTTON).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.ROOT).assertExists()

        // 打开页面自动用条目名搜索.
        awaitTag(ManualBrowsePageTestTags.result(0))
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()
        awaitTag(ManualBrowsePageTestTags.episode(0))
        onNodeWithTag(ManualBrowsePageTestTags.REMEMBER_SWITCH).assertDoesNotExist()

        onNodeWithTag(ManualBrowsePageTestTags.episode(0)).performClick()
        waitUntil(timeoutMillis = 10_000) { picks.isNotEmpty() }
        runOnIdle {
            val (media, memory) = picks.single()
            assertEquals("https://example.com/play/1/1", media.originalUrl)
            assertNull(memory)
        }
    }
}
