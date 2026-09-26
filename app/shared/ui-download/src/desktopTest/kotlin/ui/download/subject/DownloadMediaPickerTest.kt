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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_mode_auto
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPageTestTags
import me.him188.ani.app.ui.mediaselect.bt.BtResourcesPageTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadMediaPickerTest {
    @Composable
    private fun Picker(hasBt: Boolean) {
        ProvideCompositionLocalsForPreview {
            DownloadMediaPickerContent(
                selectorState = rememberTestMediaSelectorState(),
                sourceResults = TestMediaSourceResultListPresentation,
                hasBt = hasBt,
                watching = WatchingEpisode("01", "Episode 1"),
                fetchRequest = TestMediaFetchRequest,
                onFetchRequestChange = {},
                onRestartSource = {},
                onSelect = {},
            )
        }
    }

    private fun modeName(mode: MediaSelectorMode) = runBlocking {
        when (mode) {
            MediaSelectorMode.AUTO -> getString(Lang.media_selector_mode_auto)
            MediaSelectorMode.BT -> getString(Lang.media_selector_mode_bt)
            MediaSelectorMode.MANUAL -> error("not offered by the download dialog")
        }
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
        // 下载对话框没有手动查找入口, 自动页也不显示救援卡片.
        onNodeWithTag(AutoMatchPageTestTags.RESCUE_CARD).assertDoesNotExist()

        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.AUTO)).assertExists()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
    }
}
