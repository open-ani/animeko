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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
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
                    .padding(top = 4.dp)
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
                    modifier = Modifier.height(360.dp),
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
    
    Column(modifier.padding(horizontal = 16.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { showBottomSheet = true }
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
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
    
    if (showBottomSheet) {
        EpisodeSelectionBottomSheet(
            episodeCarouselState = episodeCarouselState,
            onDismiss = { showBottomSheet = false }
        )
    }
}
