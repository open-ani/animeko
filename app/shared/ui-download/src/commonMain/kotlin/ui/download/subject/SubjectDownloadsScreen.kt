/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.rememberDownloadSelectionState
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets

@Composable
fun SubjectDownloadsScreen(
    subjectId: Int,
    onPlay: (DownloadItem) -> Unit,
    onNavigateDownloadDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val selection = rememberDownloadSelectionState()
    SubjectDownloadsFeature(subjectId) { state, actions, sourceInfo ->
        SubjectDownloadsPage(
            state = state,
            selection = selection,
            actions = actions,
            sourceInfoProvider = sourceInfo,
            onPlay = onPlay,
            onViewDetail = { onNavigateDownloadDetail(it.id) },
            modifier = modifier,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
        )
    }
}
