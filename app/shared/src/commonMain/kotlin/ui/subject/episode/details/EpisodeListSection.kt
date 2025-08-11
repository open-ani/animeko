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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.List
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
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
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isDesktop = LocalPlatform.current.isDesktop()
    val isWideScreen = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    
    if (isDesktop || isWideScreen) {
        // 桌面端：保持原有的下拉展开方式
        DesktopEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = modifier
        )
    } else {
        // 移动端：横向滚动 + BottomSheet
        MobileEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            airingLabelState = airingLabelState,
            modifier = modifier
        )
    }
}

@Composable
private fun DesktopEpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp)) {
        Card(
            onClick = onToggleExpanded,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                        Icons.Outlined.List, 
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
                    containerColor = Color.Transparent
                )
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp)
            ) {
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
                
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(
                        items = episodeCarouselState.episodes,
                        key = { it.episodeId }
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
    
    Column(modifier.padding(16.dp)) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                        }
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiringLabel(
                    airingLabelState,
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showBottomSheet = true }
                ) {
                    Icon(
                        Icons.Outlined.Dataset,
                        contentDescription = "查看更多剧集"
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
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(
                items = episodeCarouselState.episodes,
                key = { it.episodeId }
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
                    }
                )
            }
        }
    }
    
    // BottomSheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { BottomSheetDefaults.windowInsets }
        ) {
            Column {
                TopAppBar(
                    title = { Text("选择剧集") },
                    actions = {
                        IconButton(
                            onClick = { showBottomSheet = false }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BottomSheetDefaults.ContainerColor
                    )
                )
                
                val gridState = rememberLazyGridState()
                
                LaunchedEffect(showBottomSheet) {
                    if (showBottomSheet) {
                        val playingIndex = episodeCarouselState.episodes.indexOfFirst { 
                            episodeCarouselState.isPlaying(it) 
                        }
                        if (playingIndex >= 0) {
                            gridState.animateScrollToItem(playingIndex)
                        }
                    }
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
                ) {
                    items(
                        items = episodeCarouselState.episodes,
                        key = { it.episodeId }
                    ) { episode ->
                        EpisodeGridItem(
                            episode = episode,
                            isPlaying = episodeCarouselState.isPlaying(episode),
                            onClick = { 
                                episodeCarouselState.onSelect(episode)
                                showBottomSheet = false
                            },
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
}

@Composable
private fun EpisodeListItem(
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
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
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
                }
            )
        },
        supportingContent = {
            if (episode.episodeInfo.nameCn.isNotEmpty()) {
                Text(
                    episode.episodeInfo.nameCn,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWatched) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        LocalContentColor.current.copy(alpha = 0.7f)
                    }
                )
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
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                        AnimatedEqualizer(
                            color = MaterialTheme.colorScheme.primary
                        )
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
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

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
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = modifier
            .aspectRatio(1.5f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                episode.episodeInfo.sort.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else if (isWatched) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    LocalContentColor.current
                },
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                episode.episodeInfo.displayName,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isWatched) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    LocalContentColor.current
                }
            )
        }
    }
}

@Composable
private fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Canvas(
        modifier = modifier.size(16.dp)
    ) {
        val barWidth = size.width / 5f
        val maxHeight = size.height
        val cornerRadius = CornerRadius(barWidth / 2f)
        
        // Bar 1
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, maxHeight * (1f - bar1Height)),
            size = Size(barWidth, maxHeight * bar1Height),
            cornerRadius = cornerRadius
        )
        
        // Bar 2
        drawRoundRect(
            color = color,
            topLeft = Offset(barWidth * 2f, maxHeight * (1f - bar2Height)),
            size = Size(barWidth, maxHeight * bar2Height),
            cornerRadius = cornerRadius
        )
        
        // Bar 3
        drawRoundRect(
            color = color,
            topLeft = Offset(barWidth * 4f, maxHeight * (1f - bar3Height)),
            size = Size(barWidth, maxHeight * bar3Height),
            cornerRadius = cornerRadius
        )
    }
}