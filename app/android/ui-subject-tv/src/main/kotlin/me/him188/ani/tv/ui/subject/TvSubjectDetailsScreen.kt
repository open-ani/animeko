/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults

/**
 * TV 条目详情页 (atv-architecture.md §7.5, M1 精简版):
 * 全屏 backdrop + 贴底信息带 (标题/评分/简介) + 播放按钮 + 选集行.
 */
@Composable
fun TvSubjectDetailsScreen(
    viewModel: TvSubjectDetailsViewModel,
    onPlayEpisode: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject by viewModel.subject.collectAsState()

    Box(modifier.fillMaxSize()) {
        val info = subject
        if (info == null) {
            Text(
                "加载中…",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            SubjectContent(info, onPlayEpisode)
        }
    }
}

@Composable
private fun SubjectContent(
    info: SubjectCollectionInfo,
    onPlayEpisode: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectInfo = info.subjectInfo
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 续播目标: 第一个未看完的集, 否则第一集
    val playTarget = info.episodes.firstOrNull { !it.collectionType.isDoneOrDropped() }
        ?: info.episodes.firstOrNull()

    Box(modifier.fillMaxSize()) {
        // 全屏 backdrop (M1 以海报 Crop 充当, TMDB 横版图后补)
        AsyncImage(
            model = subjectInfo.imageLarge,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to surfaceColor.copy(alpha = 0.55f),
                        0.55f to surfaceColor.copy(alpha = 0.86f),
                        1.0f to surfaceColor,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                subjectInfo.displayName,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (subjectInfo.ratingInfo.score.isNotBlank()) {
                    Text(
                        "★ ${subjectInfo.ratingInfo.score}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "全 ${subjectInfo.totalEpisodes} 话",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (subjectInfo.summary.isNotBlank()) {
                Text(
                    subjectInfo.summary,
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(0.55f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (playTarget != null) {
                Button(
                    onClick = { onPlayEpisode(playTarget.episodeId) },
                    Modifier.padding(top = 16.dp),
                ) {
                    Text("立即播放 第 ${playTarget.episodeInfo.sort} 话")
                }
            }

            Text(
                "选集",
                Modifier.padding(top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 48.dp),
            ) {
                items(info.episodes, key = { it.episodeId }) { episode ->
                    TvEpisodeCard(
                        episode = episode,
                        onClick = { onPlayEpisode(episode.episodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: EpisodeCollectionInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = episode.collectionType.isDoneOrDropped()
    Surface(
        onClick = onClick,
        modifier = modifier.width(180.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
        border = TvFocusDefaults.clickableCardBorder(),
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .height(52.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                episode.episodeInfo.sort.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (watched) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    Color.Unspecified
                },
            )
            Text(
                episode.episodeInfo.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
