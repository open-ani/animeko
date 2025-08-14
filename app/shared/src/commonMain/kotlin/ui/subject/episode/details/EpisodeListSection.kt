/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeCard
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeSelectionBottomSheet
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.platform.isDesktop

@Composable
fun EpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
) {
    if (LocalPlatform.current.isDesktop()) {
        // 桌面端：下拉展开
        DesktopEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = modifier,
        )
    } else {
        // 移动端：横向滚动 + BottomSheet
        MobileEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            airingLabelState = airingLabelState,
            modifier = modifier,
        )
    }
}

@Composable
private fun DesktopEpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
) {
    Box(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp,
                    ),
                    modifier = Modifier.fillMaxWidth().offset(y = (-1).dp),
                ) {
                    Column(modifier = Modifier.padding(top = 64.dp)) {
                        val listState = rememberLazyListState()

                        LaunchedEffect(expanded) {
                            if (expanded) {
                                val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                                    episodeCarouselState.isPlaying(it)
                                }
                                if (playingIndex >= 0) {
                                    listState.animateScrollToItem(playingIndex)
                                }
                            }
                        }

                        if (episodeCarouselState.episodes.size > 100) {
                            PaginatedEpisodeList(
                                episodes = episodeCarouselState.episodes,
                                episodeCarouselState = episodeCarouselState,
                                listState = listState,
                                modifier = Modifier.height(360.dp),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(360.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(
                                    items = episodeCarouselState.episodes,
                                    key = { it.episodeId },
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
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        "剧集列表",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier.combinedClickable { onToggleExpanded() },
            )
        }
    }
}

@Composable
private fun MobileEpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val horizontalListState = rememberLazyListState()
    var hasInitialScrolled by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 16.dp)) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "剧集列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = {
                            val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                                episodeCarouselState.isPlaying(it)
                            }
                            if (playingIndex >= 0) {
                                coroutineScope.launch {
                                    horizontalListState.animateScrollToItem(playingIndex)
                                }
                            }
                        },
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiringLabel(
                    airingLabelState,
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { showBottomSheet = true },
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "查看更多剧集",
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 初始滚动到正在播放的剧集
        LaunchedEffect(episodeCarouselState.episodes) {
            if (!hasInitialScrolled && episodeCarouselState.episodes.isNotEmpty()) {
                val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                    episodeCarouselState.isPlaying(it)
                }
                if (playingIndex >= 0) {
                    horizontalListState.animateScrollToItem(playingIndex)
                    hasInitialScrolled = true
                }
            }
        }

        // 横向滚动的剧集列表

        LazyRow(
            state = horizontalListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(
                items = episodeCarouselState.episodes,
                key = { it.episodeId },
            ) { episode ->
                EpisodeCard(
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
            }
        }
    }

    if (showBottomSheet) {
        EpisodeSelectionBottomSheet(
            episodeCarouselState = episodeCarouselState,
            onDismiss = { showBottomSheet = false },
        )
    }
}

@Composable
private fun PaginatedEpisodeList(
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
                title = "第 $startEp-$endEp 集",
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

    LaunchedEffect(playingEpisodeIndex) {
        if (playingEpisodeIndex >= 0) {
            val targetGroupIndex = playingEpisodeIndex / 100
            currentGroupIndex = targetGroupIndex
            val episodeIndexInGroup = playingEpisodeIndex % 100
            listState.animateScrollToItem(episodeIndexInGroup)
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            episodeGroups.getOrNull(currentGroupIndex)?.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 4.dp),
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

        // Episode List
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            val currentGroup = episodeGroups.getOrNull(currentGroupIndex)
            if (currentGroup != null) {
                items(
                    items = currentGroup.episodes,
                    key = { "${currentGroupIndex}_${it.episodeId}" },
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
                        },
                    )
                }
            }
        }
    }
}

private data class EpisodeGroup(
    val title: String,
    val episodes: List<EpisodeCollectionInfo>,
    val startIndex: Int
)
