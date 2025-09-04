package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped

/**
 * 分页剧集列表组件，用于处理大量剧集的性能优化显示。
 * 
 * 将剧集按100集为一组进行分组，提供分组导航和快速跳转功能。
 * 适用于超过100集的长篇番剧，避免一次性渲染大量列表项导致的性能问题。
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val episodeGroups = remember(episodes) {
        episodes.chunked(100).mapIndexed { index, chunk ->
            val startEp = index * 100 + 1
            val endEp = startEp + chunk.size - 1
            EpisodeGroup(
                title = "第 $startEp-$endEp 话",
                episodes = chunk,
                startIndex = index * 100,
            )
        }
    }

    val playingEpisodeIndex = remember(episodes) {
        episodes.indexOfFirst { episodeCarouselState.isPlaying(it) }
    }

    val initialCurrentGroup = remember(playingEpisodeIndex) {
        if (playingEpisodeIndex >= 0) playingEpisodeIndex / 100 else 0
    }

    var currentGroupIndex by remember { mutableStateOf(initialCurrentGroup) }
    var showGroupSelector by remember { mutableStateOf(false) }
    var isInitialPositioning by remember { mutableStateOf(true) }

    // 计算每个分组在LazyColumn中的起始位置
    val groupStartIndices = remember(episodeGroups) {
        var index = 0
        episodeGroups.map { group ->
            val startIndex = index
            index++ // group header
            index += group.episodes.size // episodes
            startIndex
        }
    }

    LaunchedEffect(playingEpisodeIndex) {
        if (playingEpisodeIndex >= 0 && isInitialPositioning) {
            val targetGroupIndex = playingEpisodeIndex / 100
            currentGroupIndex = targetGroupIndex
            // Calculate the exact position: all previous groups (headers + episodes) + current group header + episode position
            val episodePositionInGroup = playingEpisodeIndex % 100
            var itemIndex = 0
            // Add all previous groups (header + episodes)
            for (i in 0 until targetGroupIndex) {
                itemIndex += 1 + episodeGroups[i].episodes.size // header + episodes
            }
            // Add current group header + episode position
            itemIndex += 1 + episodePositionInGroup // +1 for current group header
            listState.animateScrollToItem(itemIndex)
            isInitialPositioning = false
        }
    }

    LaunchedEffect(currentGroupIndex) {
        if (!isInitialPositioning) {
            val targetIndex = groupStartIndices.getOrNull(currentGroupIndex) ?: 0
            listState.animateScrollToItem(targetIndex)
        }
    }



    Column(modifier = modifier) {
        // Sticky Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (currentGroupIndex > 0) {
                        currentGroupIndex--
                    }
                },
                enabled = currentGroupIndex > 0,
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "上一组",
                    tint = if (currentGroupIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f,
                    ),
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = { showGroupSelector = true },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            episodeGroups.getOrNull(currentGroupIndex)?.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 4.dp),
                            maxLines = 1
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = "选择分组",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DropdownMenu(
                    expanded = showGroupSelector,
                    onDismissRequest = { showGroupSelector = false },
                ) {
                    episodeGroups.forEachIndexed { index, group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    group.title,
                                    color = if (index == currentGroupIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                currentGroupIndex = index
                                showGroupSelector = false
                            },
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    if (currentGroupIndex < episodeGroups.size - 1) {
                        currentGroupIndex++
                    }
                },
                enabled = currentGroupIndex < episodeGroups.size - 1,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "下一组",
                    tint = if (currentGroupIndex < episodeGroups.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f,
                    ),
                )
            }
        }

        // Episode List with all episodes and group headers
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            episodeGroups.forEachIndexed { groupIndex, group ->
                item(key = "header_$groupIndex") {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(
                    items = group.episodes,
                    key = { "PaginatedEpisodeList_${groupIndex}_${it.episodeId}" }
                ) { episode ->
                    EpisodeListItem(
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
                        }
                    )
                }
            }
        }
    }
}

/**
 * 剧集列表项组件，用于桌面端垂直列表中的单个剧集显示。
 *
 * 适用于桌面端的展开列表和分页列表中，提供清晰的信息层次和交互反馈。
 */
@Composable
fun EpisodeListItem(
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
            }
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
                overflow = TextOverflow.Ellipsis
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
                onLongClick = onLongClick
            )
    )
}


/**
 * 剧集分组数据类，用于将大量剧集按组组织。
 */
private data class EpisodeGroup(
    val title: String,
    val episodes: List<EpisodeCollectionInfo>,
    val startIndex: Int
)