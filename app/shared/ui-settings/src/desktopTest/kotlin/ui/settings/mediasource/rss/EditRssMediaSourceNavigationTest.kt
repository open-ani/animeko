/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_name
import me.him188.ani.app.ui.lang.settings_mediasource_rss_test
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test

class EditRssMediaSourceNavigationTest {
    @Test
    fun `compact editor can open the test pane`() = verifyEditor(400, singlePane = true)

    @Test
    fun `medium editor can open the test pane`() = verifyEditor(720, singlePane = true)

    @Test
    fun `expanded editor shows editing and testing together`() = verifyEditor(1000, singlePane = false)

    @Test
    fun `large editor shows editing and testing together`() = verifyEditor(1440, singlePane = false)

    private fun verifyEditor(width: Int, singlePane: Boolean) = runAniComposeUiTest {
        var nameLabel = ""
        var testLabel = ""
        setContent {
            val windowInfo = LocalWindowInfo.current
            val density = Density(0.5f)
            val size = with(density) { IntSize(width.dp.roundToPx(), 768.dp.roundToPx()) }
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalWindowInfo provides object : WindowInfo by windowInfo {
                    override val containerSize: IntSize = size
                },
            ) {
                ProvideCompositionLocalsForPreview {
                    nameLabel = stringResource(Lang.settings_mediasource_rss_name)
                    testLabel = stringResource(Lang.settings_mediasource_rss_test)
                    val (edit, test) = rememberTestEditRssMediaSourceStateAndRssTestPaneState()
                    Box(Modifier.requiredSize(width.dp, 768.dp).testTag("rssEditor")) {
                        EditRssMediaSourceScreen(edit, test, {})
                    }
                }
            }
        }

        onNodeWithText(nameLabel).assertIsDisplayed()
        if (singlePane) {
            onNodeWithText(testLabel).assertIsDisplayed().performClick()
            onNodeWithText("查询结果").assertIsDisplayed()
        } else {
            onNodeWithText("查询结果").assertIsDisplayed()
            onNodeWithText(testLabel).assertDoesNotExist()
        }
    }
}
