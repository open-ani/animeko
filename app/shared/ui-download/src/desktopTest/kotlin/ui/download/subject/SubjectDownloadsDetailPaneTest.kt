/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.download.components.rememberDownloadSelectionState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.downloads_empty
import org.jetbrains.compose.resources.getString

class SubjectDownloadsDetailPaneTest {
    @Test
    fun `pane without presenter shows the loading state`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsDetailPane(
                    presenter = null,
                    loadingTitle = "Loading subject",
                    selectionState = rememberDownloadSelectionState(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithTag(SubjectDownloadsTestTags.LOADING).assertExists()
        onNodeWithText("Loading subject").assertExists()
        onNodeWithText(runBlocking { getString(Lang.downloads_empty) }).assertDoesNotExist()
    }
}
