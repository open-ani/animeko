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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped

/**
 * 剧集网格组件，以两列网格布局显示剧集列表。
 *
 * 支持自动滚动到当前播放的剧集。
 */
@Composable
fun EpisodeGrid(
    episodeCarouselState: EpisodeCarouselState,
    onEpisodeClick: (EpisodeCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    val gridState = rememberLazyGridState()
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val playingIndex = episodeCarouselState.episodes.indexOfFirst { 
                episodeCarouselState.isPlaying(it) 
            }
            if (playingIndex >= 0) {
                gridState.animateScrollToItem(playingIndex)
            }
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(all = 16.dp).plus(WindowInsets.navigationBars.asPaddingValues()),
        userScrollEnabled = true,
        modifier = modifier.heightIn(max = 500.dp)
    ) {
        items(
            items = episodeCarouselState.episodes,
            key = { it.episodeId }
        ) { episode ->
            EpisodeGridItem(
                episode = episode,
                isPlaying = episodeCarouselState.isPlaying(episode),
                onClick = { onEpisodeClick(episode) },
                onLongClick = {
                    val newType = if (episode.collectionType.isDoneOrDropped()) {
                        UnifiedCollectionType.NOT_COLLECTED
                    } else {
                        UnifiedCollectionType.DONE
                    }
                    episodeCarouselState.setCollectionType(episode, newType)
                }
            )
        }
    }
}

/**
 * 剧集网格项组件，用于网格布局中的单个剧集显示。
 * 
 * 与EpisodeCard相比，该组件采用更紧凑的垂直布局，适合网格环境下的显示。
 * 支持与其他剧集组件相同的状态显示和交互行为。
 * 
 * @param episode 剧集收藏信息，包含剧集详情和收藏状态
 * @param isPlaying 是否为当前播放的剧集
 * @param onClick 点击回调，通常用于切换到该剧集
 * @param onLongClick 长按回调，通常用于快速标记观看状态
 * @param modifier 修饰符
 * 
 * ## 视觉状态
 * - **正在播放**：主色容器背景，显示播放图标，主色文字
 * - **已观看**：半透明背景，淡化文字颜色
 * - **未观看**：正常背景和文字颜色
 * 
 * ## 布局特性
 * - **固定高度**：72dp，适合网格布局
 * - **垂直布局**：编号和标题垂直排列，节省水平空间
 * - **文字截断**：标题过长时显示省略号
 */
@Composable
private fun EpisodeGridItem(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWatched = episode.collectionType.isDoneOrDropped()
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPlaying) {
                    PlayingIcon()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "${episode.episodeInfo.sort}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else if (isWatched) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        LocalContentColor.current
                    }
                )
            }
            Text(
                episode.episodeInfo.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isWatched) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}