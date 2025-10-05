/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.foundation.lists.PaginatedGroup
import me.him188.ani.app.ui.foundation.lists.PaginatedList
import me.him188.ani.app.ui.foundation.lists.rememberPaginatedListState
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped

/**
 * 分页剧集列表组件，用于处理大量剧集的性能优化显示。
 *
 * 将剧集按100集为一组进行分组，提供分组导航和快速跳转功能。
 *
 * - 每100集为一组，显示为“第1-100话”格式
 * - 顶部固定导航栏，支持上下翻页和下拉选择
 * - 初始化时自动滚动到当前播放的剧集
 * - 每个分组在列表中显示标题分隔
 * - 支持点击切换剧集和长按标记观看状态
 */
@Composable
fun PaginatedEpisodeList(
    episodes: List<EpisodeCollectionInfo>,
    episodeCarouselState: EpisodeCarouselState,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    // 生成分组数据
    val groups = remember(episodes) {
        episodes.chunked(100).mapIndexed { groupIndex, chunk ->
            val startItemIndex = groupIndex * 100
            val startEp = groupIndex * 100 + 1
            val endEp = startEp + chunk.size - 1
            PaginatedGroup(
                title = "第 $startEp-$endEp 话",
                items = chunk,
                startIndex = startItemIndex,
                groupIndex = groupIndex,
            )
        }
    }

    val playingEpisodeIndex by rememberSaveable(episodes) {
        derivedStateOf { episodes.indexOfFirst { episodeCarouselState.isPlaying(it) } }
    }

    val state = rememberPaginatedListState(groups, episodes, listState)

    // 统一的播放位置处理
    LaunchedEffect(playingEpisodeIndex) {
        if (playingEpisodeIndex >= 0) {
            state.bringIntoView(playingEpisodeIndex)
        }
    }

    PaginatedList(
        state = state,
        modifier = modifier,
        onItemClick = { episode ->
            episodeCarouselState.onSelect(episode)
        },
        headerContent = { group ->
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        },
        itemContent = { episode ->
            EpisodeDetailsListItem(
                episode = episode,
                isPlaying = episodeCarouselState.isPlaying(episode),
                onClick = { episodeCarouselState.onSelect(episode) },
                onLongClick = {
                    val newType = if (episode.collectionType.isDoneOrDropped()) {
                        UnifiedCollectionType.NOT_COLLECTED
                    } else {
                        UnifiedCollectionType.DONE
                    }
                    episodeCarouselState.setCollectionType(episode, newType)
                },
            )
        },
    )
}

/**
 * 剧集列表项组件，用于宽屏垂直列表中的单个剧集显示。
 */
@Composable
private fun EpisodeDetailsListItem(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWatched = episode.collectionType.isDoneOrDropped()

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        headlineContent = {
            Text(
                "${episode.episodeInfo.sort}  ${episode.episodeInfo.displayName}",
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else if (isWatched) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    LocalContentColor.current
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (isPlaying) {
                PlayingIcon()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
