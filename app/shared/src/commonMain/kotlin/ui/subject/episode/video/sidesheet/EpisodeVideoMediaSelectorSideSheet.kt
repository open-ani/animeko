/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.subject.episode.TAG_MEDIA_SELECTOR_SHEET
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorView
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsView
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.MediaSelectorSheet(
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultListPresentation: MediaSourceResultListPresentation,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onRestartSource: (MediaSourceResultPresentation) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideSheetLayout(
        title = { Text(text = "选择数据源") },
        onDismissRequest = onDismissRequest,
        Modifier.testTag(TAG_MEDIA_SELECTOR_SHEET),
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        },
    ) {
        MediaSelectorView(
            mediaSelectorState,
            sourceResults = {
                MediaSourceResultsView(
                    mediaSourceResultListPresentation,
                    mediaSelectorState,
                    onRefresh,
                    onRestartSource,
                )
            },
            modifier.padding(horizontal = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            stickyHeaderBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            itemProgressBar = {},
            onClickItem = {
                mediaSelectorState.select(it)
                onDismissRequest()
            },
            singleLineFilter = true,
        )
    }
}
