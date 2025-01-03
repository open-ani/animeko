/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.TestMediaSourceInfo
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.media.renderProperties
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingSummary
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
fun TestEpisodeWatchStatusButton() {
    var isDone by rememberSaveable { mutableStateOf(false) }
    EpisodeWatchStatusButton(
        isDone = isDone,
        onUnmark = { isDone = false },
        onMarkAsDone = { isDone = true },
    )
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewEpisodeItemImpl(
    media: DefaultMedia? = TestMediaList[0],
    episodeTitle: String = "中文剧集名称",
    filename: String? = "filename-".repeat(3) + ".mkv",
    videoLoadingState: VideoLoadingState = VideoLoadingState.Succeed(false),
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .animateContentSize(),
    ) {
        PlayingEpisodeItem(
            episodeSort = { Text("01") },
            title = { Text(episodeTitle) },
            watchStatus = { TestEpisodeWatchStatusButton() },
            mediaSelected = media != null,
            mediaLabels = {
                media?.let {
                    Text(media.renderProperties())
                }
            },
            filename = {
                filename?.let {
                    Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            },
            videoLoadingSummary = {
                VideoLoadingSummary(
                    state = videoLoadingState,
                )
            },
            mediaSource = {
                var isLoading by remember { mutableStateOf(false) }
                PlayingEpisodeItemDefaults.MediaSource(
                    media = null,
                    mediaSourceInfo = TestMediaSourceInfo,
                    isLoading = isLoading,
                    onClick = { isLoading = !isLoading },
                    modifier = Modifier.clickable {
                        isLoading = !isLoading
                    },
                )
            },
            actions = {
                PlayingEpisodeItemDefaults.ActionShare(media)
                PlayingEpisodeItemDefaults.ActionCache({ })
            },
        )
    }
}


@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItem() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl()
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemNoFilename() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(filename = null)
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemLongTexts() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        episodeTitle = "超长名称".repeat(20),
        filename = "filename-".repeat(20) + ".mkv",
    )
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeNotSelected() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        media = null,
        filename = null,
    )
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemFailed() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        videoLoadingState = VideoLoadingState.UnsupportedMedia,
    )
}
