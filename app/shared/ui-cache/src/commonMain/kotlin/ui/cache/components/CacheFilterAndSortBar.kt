/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.ui.cache.CacheListEntry
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Stable
internal class CacheFilterAndSortState {
    var selectedCollectionType by mutableStateOf<UnifiedCollectionType?>(null)
    var selectedEngineKey by mutableStateOf<MediaCacheEngineKey?>(null)
    var selectedStatus by mutableStateOf<CacheStatusFilter?>(null)
    var sortOption by mutableStateOf(CacheSortOption.Newest)

    @Composable
    fun applyFilterAndSort(list: List<CacheListEntry>): List<CacheListEntry> {
        val result by remember(list) {
            derivedStateOf {
                val filtered = list.filter { entry ->
                    (selectedCollectionType == null || (entry.collectionType
                        ?: UnifiedCollectionType.NOT_COLLECTED) == selectedCollectionType) &&
                            (selectedEngineKey == null || entry.engineKey == selectedEngineKey) &&
                            (selectedStatus == null || entry.status == selectedStatus)
                }

                when (sortOption) {
                    CacheSortOption.Newest -> filtered.sortedByDescending { it.episode.creationTime ?: Long.MIN_VALUE }
                    CacheSortOption.Oldest -> filtered.sortedBy { it.episode.creationTime ?: Long.MIN_VALUE }
                    CacheSortOption.SubjectAsc -> filtered.sortedBy { it.subjectName }
                    CacheSortOption.SubjectDesc -> filtered.sortedByDescending { it.subjectName }
                    CacheSortOption.EpisodeAsc -> filtered.sortedWith(compareBy({ it.groupId }, { it.episode.sort }))
                    CacheSortOption.EpisodeDesc -> filtered.sortedWith(
                        compareBy<CacheListEntry>({ it.groupId }, { it.episode.sort }).reversed(),
                    )
                }
            }
        }
        return result
    }

    companion object {
        val TheSaver = Saver<CacheFilterAndSortState, String>(
            save = { state ->
                buildString {
                    append(state.selectedCollectionType?.ordinal ?: "-1")
                    append(",")
                    append(state.selectedEngineKey?.key ?: "")
                    append(",")
                    append(state.selectedStatus?.ordinal ?: "-1")
                    append(",")
                    append(state.sortOption.ordinal)
                }
            },
            restore = { restored ->
                val parts = restored.split(",")
                CacheFilterAndSortState().apply {
                    selectedCollectionType =
                        parts.getOrNull(0)?.toIntOrNull()?.let { ord ->
                            UnifiedCollectionType.entries.getOrNull(ord)
                        }
                    selectedEngineKey =
                        parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { key ->
                            MediaCacheEngineKey(key)
                        }
                    selectedStatus =
                        parts.getOrNull(2)?.toIntOrNull()?.let { ord ->
                            CacheStatusFilter.entries.getOrNull(ord)
                        }
                    sortOption =
                        parts.getOrNull(3)?.toIntOrNull()?.let { ord ->
                            CacheSortOption.entries.getOrNull(ord)
                        } ?: CacheSortOption.Newest
                }
            },
        )
    }
}

@Composable
internal fun rememberCacheFilterAndSortState(): CacheFilterAndSortState {
    return rememberSaveable(saver = CacheFilterAndSortState.TheSaver) {
        CacheFilterAndSortState()
    }
}

@Composable
internal fun CacheFilterAndSortBar(
    state: CacheFilterAndSortState = rememberCacheFilterAndSortState(),
    mediaCacheEngineOptions: List<MediaCacheEngineKey>,
    modifier: Modifier = Modifier,
    containerColor: Color,
) {

    Surface(color = containerColor) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CacheFilterRow(
                selectedCollectionType = state.selectedCollectionType,
                onCollectionTypeChange = { state.selectedCollectionType = it },
                selectedEngine = state.selectedEngineKey,
                engineOptions = mediaCacheEngineOptions,
                onEngineChange = { state.selectedEngineKey = it },
                selectedStatus = state.selectedStatus,
                onStatusChange = { state.selectedStatus = it },
                modifier = Modifier.weight(1f),
            )

            SortMenuButton(
                sortOption = state.sortOption,
                onSortOptionChange = { state.sortOption = it },
            )
        }
    }
}


@Composable
private fun CacheFilterRow(
    selectedCollectionType: UnifiedCollectionType?,
    onCollectionTypeChange: (UnifiedCollectionType?) -> Unit,
    selectedEngine: MediaCacheEngineKey?,
    engineOptions: List<MediaCacheEngineKey>,
    onEngineChange: (MediaCacheEngineKey?) -> Unit,
    selectedStatus: CacheStatusFilter?,
    onStatusChange: (CacheStatusFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollectionFilterChip(selectedCollectionType, onCollectionTypeChange)
        EngineFilterChip(selectedEngine, engineOptions, onEngineChange)
        StatusFilterChip(selectedStatus, onStatusChange)
    }
}

@Composable
private fun CollectionFilterChip(
    selected: UnifiedCollectionType?,
    onChange: (UnifiedCollectionType?) -> Unit,
) {
    FilterPill(
        label = "收藏状态",
        selectedLabel = selected?.let { renderCollectionType(it) },
        isSelected = selected != null,
        onClick = { isSelected ->
            if (isSelected) onChange(null)
        },
    ) { onDismiss ->
        UnifiedCollectionType.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(renderCollectionType(option)) },
                onClick = {
                    onChange(option)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun EngineFilterChip(
    selected: MediaCacheEngineKey?,
    options: List<MediaCacheEngineKey>,
    onChange: (MediaCacheEngineKey?) -> Unit,
) {
    FilterPill(
        label = "缓存类型",
        selectedLabel = selected?.let { renderEngineKey(it) },
        isSelected = selected != null,
        enabled = options.isNotEmpty(),
        onClick = { isSelected ->
            if (isSelected) onChange(null)
        },
    ) { onDismiss ->
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(renderEngineKey(option)) },
                onClick = {
                    onChange(option)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun StatusFilterChip(
    selected: CacheStatusFilter?,
    onChange: (CacheStatusFilter?) -> Unit,
) {
    FilterPill(
        label = "下载状态",
        selectedLabel = selected?.let {
            when (it) {
                CacheStatusFilter.Downloading -> "下载中"
                CacheStatusFilter.Finished -> "已完成"
            }
        },
        isSelected = selected != null,
        onClick = { isSelected ->
            if (isSelected) onChange(null)
        },
    ) { onDismiss ->
        CacheStatusFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (option) {
                            CacheStatusFilter.Downloading -> "下载中"
                            CacheStatusFilter.Finished -> "已完成"
                        },
                    )
                },
                onClick = {
                    onChange(option)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selectedLabel: String?,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: (isSelected: Boolean) -> Unit,
    dropdownContent: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = isSelected,
            onClick = {
                if (!enabled) return@FilterChip
                if (isSelected) {
                    onClick(true)
                } else {
                    showMenu = true
                }
            },
            label = { Text(selectedLabel ?: label) },
            trailingIcon = if (isSelected) {
                {
                    Icon(Icons.Default.FilterList, null)
                }
            } else null,
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(),
            enabled = enabled,
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            dropdownContent { showMenu = false }
        }
    }
}

@Composable
private fun SortMenuButton(
    sortOption: CacheSortOption,
    onSortOptionChange: (CacheSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { showSortMenu = true },
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, "排序")
        }
        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            CacheSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == sortOption) {
                            Icon(Icons.Rounded.Check, null)
                        }
                    },
                    onClick = {
                        onSortOptionChange(option)
                        showSortMenu = false
                    },
                )
            }
        }
    }
}

internal enum class CacheStatusFilter {
    Downloading,
    Finished,
}

internal enum class CacheSortOption(val label: String) {
    Newest("最新下载"),
    Oldest("最早下载"),
    SubjectAsc("条目名 AZ"),
    SubjectDesc("条目名 ZA"),
    EpisodeAsc("剧集升序"),
    EpisodeDesc("剧集降序"),
}

private fun renderCollectionType(type: UnifiedCollectionType): String {
    return when (type) {
        UnifiedCollectionType.WISH -> "想看"
        UnifiedCollectionType.DOING -> "在看"
        UnifiedCollectionType.DONE -> "看过"
        UnifiedCollectionType.ON_HOLD -> "搁置"
        UnifiedCollectionType.DROPPED -> "抛弃"
        UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
    }
}

private fun renderEngineKey(key: MediaCacheEngineKey): String = when (key) {
    MediaCacheEngineKey.Anitorrent -> "BT"
    MediaCacheEngineKey.WebM3u -> "Web"
    else -> key.key
}