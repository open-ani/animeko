package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped

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
        if (playingEpisodeIndex >= 0) {
            val targetGroupIndex = playingEpisodeIndex / 100
            currentGroupIndex = targetGroupIndex
            listState.animateScrollToItem(playingEpisodeIndex + targetGroupIndex + 1) // +1 for each group header
        }
    }

    LaunchedEffect(currentGroupIndex) {
        val targetIndex = groupStartIndices.getOrNull(currentGroupIndex) ?: 0
        listState.animateScrollToItem(targetIndex)
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
                    key = { "${groupIndex}_${it.episodeId}" }
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

private data class EpisodeGroup(
    val title: String,
    val episodes: List<EpisodeCollectionInfo>,
    val startIndex: Int
)