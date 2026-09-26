/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaSelectorModeChipTest {
    private fun AniComposeUiTest.render(
        mode: MediaSelectorMode = MediaSelectorMode.AUTO,
        showBt: Boolean = true,
        showManual: Boolean = true,
        enabled: Boolean = true,
        onModeChange: (MediaSelectorMode) -> Unit = {},
    ) {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaSelectorModeChip(
                    mode = mode,
                    onModeChange = onModeChange,
                    showBt = showBt,
                    showManual = showManual,
                    enabled = enabled,
                )
            }
        }
    }

    private fun AniComposeUiTest.openMenu() {
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertIsDisplayed().performClick()
        waitForIdle()
    }

    @Test
    fun `menu is closed until the chip is clicked`() = runAniComposeUiTest {
        render()

        for (mode in MediaSelectorMode.entries) {
            onNodeWithTag(MediaSelectorChromeTestTags.modeItem(mode)).assertDoesNotExist()
        }
    }

    @Test
    fun `clicking the chip lists all three modes`() = runAniComposeUiTest {
        render()
        openMenu()

        for (mode in MediaSelectorMode.entries) {
            onNodeWithTag(MediaSelectorChromeTestTags.modeItem(mode)).assertIsDisplayed()
        }
    }

    @Test
    fun `showBt false hides the BT item only`() = runAniComposeUiTest {
        render(showBt = false)
        openMenu()

        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.AUTO)).assertIsDisplayed()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertIsDisplayed()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).assertDoesNotExist()
    }

    @Test
    fun `showManual false hides the MANUAL item only`() = runAniComposeUiTest {
        render(showManual = false)
        openMenu()

        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.AUTO)).assertIsDisplayed()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).assertIsDisplayed()
    }

    @Test
    fun `clicking an item reports that mode and closes the menu`() = runAniComposeUiTest {
        var selected: MediaSelectorMode? = null
        render(onModeChange = { selected = it })
        openMenu()

        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).performClick()
        waitForIdle()

        assertEquals(MediaSelectorMode.MANUAL, selected)
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).assertDoesNotExist()
    }

    @Test
    fun `clicking the current mode still reports it`() = runAniComposeUiTest {
        var selected: MediaSelectorMode? = null
        render(mode = MediaSelectorMode.BT, onModeChange = { selected = it })
        openMenu()

        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).performClick()
        waitForIdle()

        assertEquals(MediaSelectorMode.BT, selected)
    }

    @Test
    fun `disabled chip does not open the menu`() = runAniComposeUiTest {
        var selected: MediaSelectorMode? = null
        render(enabled = false, onModeChange = { selected = it })

        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertIsNotEnabled().performClick()
        waitForIdle()

        for (mode in MediaSelectorMode.entries) {
            onNodeWithTag(MediaSelectorChromeTestTags.modeItem(mode)).assertDoesNotExist()
        }
        assertNull(selected)
    }
}
