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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped

@Composable
fun EpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
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
                LazyColumn(
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
private fun EpisodeListItem(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        headlineContent = {
            Text(
                "${episode.episodeInfo.sort}  ${episode.episodeInfo.displayName}",
                color = if (isPlaying) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        },
        supportingContent = {
            if (episode.episodeInfo.nameCn.isNotEmpty()) {
                Text(
                    episode.episodeInfo.nameCn,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            if (episode.collectionType.isDoneOrDropped()) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "已看",
                    tint = MaterialTheme.colorScheme.primary
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