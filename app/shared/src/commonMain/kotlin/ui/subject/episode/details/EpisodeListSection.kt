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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeGrid
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.details.components.PaginatedEpisodeList
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.platform.isDesktop

/**
 * 剧集列表区域组件，根据平台自适应显示不同的UI布局。
 * 
 * 在桌面端显示为可展开/收起的下拉列表，在移动端显示为横向滚动的卡片列表配合底部弹窗。
 * 
 * @param episodeCarouselState 剧集轮播状态，包含剧集列表、当前播放状态、选择和收藏操作等
 * @param expanded 桌面端专用：是否展开剧集列表（移动端忽略此参数）
 * @param airingLabelState 播出标签状态，用于显示番剧的播出信息
 * @param onToggleExpanded 桌面端专用：切换展开/收起状态的回调（移动端忽略此参数）
 * 
 * ## 平台差异
 * - **桌面端**：垂直列表布局，支持展开/收起，超过100集时使用分页显示
 * - **移动端**：横向滚动卡片布局，点击更多按钮弹出底部选择器
 */
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

/**
 * 桌面端剧集列表组件。
 * 
 * 显示为可展开/收起的卡片式列表，点击标题栏可切换展开状态。
 * 展开时会自动滚动到当前播放的剧集。
 */
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
                    shape = MaterialTheme.shapes.medium,
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
                                modifier = Modifier.heightIn(max = 360.dp),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
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

/**
 * 移动端剧集列表组件。
 * 
 * 显示为横向滚动的卡片列表，配合标题栏和更多按钮。
 * 点击更多按钮会弹出底部选择器，双击标题可快速滚动到当前播放的剧集。
 *
 */
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
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { BottomSheetDefaults.windowInsets },
            dragHandle = null,
            modifier = modifier
        ) {
            Column {
                TopAppBar(
                    title = { Text("选择剧集") },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BottomSheetDefaults.ContainerColor
                    )
                )

                EpisodeGrid(
                    episodeCarouselState = episodeCarouselState,
                    onEpisodeClick = { episode ->
                        episodeCarouselState.onSelect(episode)
                        showBottomSheet = false
                    },
                    isVisible = true
                )
            }
        }
    }
}

/**
 * 剧集卡片组件，用于移动端横向滚动列表中显示单个剧集。
 *
 * 显示为紧凑的卡片式布局，包含剧集编号、标题和状态指示器。
 * 根据剧集的播放和观看状态显示不同的视觉效果。
 */
@Composable
private fun EpisodeCard(
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
            .width(120.dp)
            .height(80.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaying) {
                        PlayingIcon()
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        episode.episodeInfo.sort.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else if (isWatched) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
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
}
