/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorView
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsView
import me.him188.ani.datasources.api.Media

/**
 * 播放视频时的选择数据源
 */
@Composable
fun EpisodePlayMediaSelector(
    mediaSelector: MediaSelectorState,
    sourceResults: MediaSourceResultListPresentation,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onRestartSource: (MediaSourceResultPresentation) -> Unit,
    modifier: Modifier = Modifier,
    stickyHeaderBackgroundColor: Color = Color.Unspecified,
    onSelected: (Media) -> Unit = {},
) {
    MediaSelectorView(
        mediaSelector,
        sourceResults = {
            MediaSourceResultsView(
                sourceResults, mediaSelector,
                onRefresh = onRefresh,
                onRestartSource = onRestartSource,
            )
        },
        modifier.padding(vertical = 12.dp, horizontal = 16.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        stickyHeaderBackgroundColor = stickyHeaderBackgroundColor,
        itemProgressBar = {},
        onClickItem = {
            mediaSelector.select(it)
            onSelected(it)
        },
        bottomActions = {
            TextButton(onDismissRequest) {
                Text("取消")
            }
        },
    )
}