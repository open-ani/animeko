/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.doesNotExist
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.danmaku.api.DanmakuLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuStylePickerTest {
    @Test
    fun `default style is white and floating`() {
        assertEquals(0xFFFFFF, DanmakuSendStyle.Default.color)
        assertEquals(DanmakuLocation.NORMAL, DanmakuSendStyle.Default.location)
    }

    @Test
    fun `picker - click button opens panel, selecting color and location updates style`() = runAniComposeUiTest {
        var style by mutableStateOf(DanmakuSendStyle.Default)
        setContent {
            ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
                // 弹层在按钮上方, 所以把按钮放在底部留出空间
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                    DanmakuStylePicker(style, onStyleChange = { style = it })
                }
            }
        }

        runOnIdle {
            waitUntil { onNodeWithTag(TAG_DANMAKU_STYLE_BUTTON).exists() }
            waitUntil { onNodeWithTag(TAG_DANMAKU_STYLE_PANEL).doesNotExist() }
        }

        onNodeWithTag(TAG_DANMAKU_STYLE_BUTTON).performClick()
        runOnIdle {
            waitUntil { onNodeWithTag(TAG_DANMAKU_STYLE_PANEL).exists() }
        }
        onNodeWithTag(danmakuColorSwatchTag(0xFFFFFF)).assertIsSelected()
        onNodeWithTag(danmakuLocationTileTag(DanmakuLocation.NORMAL)).assertIsSelected()

        onNodeWithTag(danmakuColorSwatchTag(0xFE0302)).performClick()
        runOnIdle {
            assertEquals(DanmakuSendStyle(0xFE0302, DanmakuLocation.NORMAL), style)
        }
        onNodeWithTag(danmakuColorSwatchTag(0xFE0302)).assertIsSelected()

        onNodeWithTag(danmakuLocationTileTag(DanmakuLocation.TOP)).performClick()
        runOnIdle {
            assertEquals(DanmakuSendStyle(0xFE0302, DanmakuLocation.TOP), style)
        }
        onNodeWithTag(danmakuLocationTileTag(DanmakuLocation.TOP)).assertIsSelected()

        // 选择后弹层保持打开, 方便继续调整
        onNodeWithTag(TAG_DANMAKU_STYLE_PANEL).assertExists()
    }

    @Test
    fun `panel - every preset color and location is selectable`() = runAniComposeUiTest {
        var style by mutableStateOf(DanmakuSendStyle.Default)
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuStylePanel(style, onStyleChange = { style = it })
            }
        }

        for (color in DanmakuSendColors.Presets) {
            onNodeWithTag(danmakuColorSwatchTag(color)).performClick()
            runOnIdle { assertEquals(color, style.color) }
        }
        for (location in DanmakuSendColors.Locations) {
            onNodeWithTag(danmakuLocationTileTag(location)).performClick()
            runOnIdle { assertEquals(location, style.location) }
        }
    }
}
