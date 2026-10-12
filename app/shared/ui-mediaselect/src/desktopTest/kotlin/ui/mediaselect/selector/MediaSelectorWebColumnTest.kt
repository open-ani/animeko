/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.selector

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediaselect.auto.AutoMatchPageTestTags
import kotlin.test.Test
import kotlin.test.assertTrue

class MediaSelectorWebColumnTest {
    private fun source(id: String, isError: Boolean) = WebSource(
        instanceId = id,
        mediaSourceId = id,
        iconUrl = "",
        name = "source $id",
        channels = emptyList(),
        isLoading = false,
        isError = isError,
        isPreferred = false,
    )

    /**
     * 名字画了两遍 (一遍透明, 用来量宽度), 位置相同, 取第一个.
     */
    private fun AniComposeUiTest.topOf(name: String): Dp =
        onAllNodesWithText(name).onFirst().getUnclippedBoundsInRoot().top

    @Test
    fun `rescue button sits after working sources and before failed ones`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MediaSelectorWebSourcesColumn(
                    listOf(source("a", isError = false), source("b", isError = true), source("c", isError = false)),
                    selectedSource = { null },
                    selectedChannel = { null },
                    onSelect = { _, _ -> },
                    onRefresh = {},
                    onResolveCaptcha = {},
                    onRequestManualSearch = {},
                )
            }
        }

        val a = topOf("source a")
        val c = topOf("source c")
        val button = onNodeWithTag(AutoMatchPageTestTags.RESCUE_BUTTON).getUnclippedBoundsInRoot().top
        val b = topOf("source b")
        assertTrue(a < c && c < button && button < b, "a=$a c=$c button=$button b=$b")
    }
}
