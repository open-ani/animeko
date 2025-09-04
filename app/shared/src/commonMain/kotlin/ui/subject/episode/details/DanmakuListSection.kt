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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.domain.episode.DanmakuFetchResultWithConfig
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.ui.DanmakuPresentation

/**
 * 弹幕列表项数据类，用于在UI中显示单条弹幕信息。
 *
 * @property id 弹幕唯一标识符
 * @property content 弹幕文本内容
 * @property timeMillis 弹幕出现时间（毫秒）
 * @property serviceId 弹幕来源服务ID
 * @property isSelf 是否为当前用户发送的弹幕
 */
data class DanmakuListItem(
    val id: String,
    val content: String,
    val timeMillis: Long,
    val serviceId: DanmakuServiceId,
    val isSelf: Boolean,
)

/**
 * 弹幕列表区域组件，提供弹幕源选择和弹幕列表显示功能。
 *
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListSection(
    danmakuFlow: Flow<List<DanmakuPresentation>>,
    fetchResults: List<DanmakuFetchResultWithConfig>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val danmakuList by danmakuFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // 获取所有可用源
    val availableSources = remember(fetchResults) {
        fetchResults.map { it.serviceId }.toSet()
    }

    // 默认全部启用
    var selectedSources by remember(availableSources) {
        mutableStateOf(availableSources)
    }

    // 初始化时启用所有源
    LaunchedEffect(availableSources) {
        if (availableSources.isNotEmpty()) {
            selectedSources = availableSources
            availableSources.forEach { serviceId ->
                onSetEnabled(serviceId, true)
            }
        }
    }

    // 显示弹幕列表，注意这里的弹幕已经是经过 [EpisodeDanmakuLoader] 过滤的
    val filteredDanmaku = remember(danmakuList) {
        danmakuList
            .map { presentation ->
                DanmakuListItem(
                    id = presentation.id,
                    content = presentation.danmaku.text,
                    timeMillis = presentation.danmaku.playTimeMillis,
                    serviceId = presentation.danmaku.serviceId,
                    isSelf = presentation.isSelf,
                )
            }
            .sortedBy { it.timeMillis }
    }


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
                        // 弹幕源chips
                        if (fetchResults.isNotEmpty()) {
                            DanmakuSourceChips(
                                fetchResults = fetchResults,
                                selectedSources = selectedSources,
                                onToggleSource = { serviceId ->
                                    selectedSources = if (serviceId in selectedSources) {
                                        onSetEnabled(serviceId, false)
                                        selectedSources - serviceId
                                    } else {
                                        onSetEnabled(serviceId, true)
                                        selectedSources + serviceId
                                    }
                                },
                                onManualMatch = onManualMatch,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }

                        // 弹幕列表
                        if (filteredDanmaku.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (fetchResults.isEmpty()) {
                                    CircularProgressIndicator()
                                } else {
                                    Text(
                                        text = if (danmakuList.isEmpty()) "暂无弹幕数据" else "没有符合筛选条件的弹幕",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.heightIn(max = 360.dp),
                            ) {
                                items(
                                    items = filteredDanmaku,
                                    key = { it.id },
                                ) { danmaku ->
                                    DanmakuListItemView(danmaku)
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
                    Text("弹幕列表")
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.FeaturedPlayList, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                },
                modifier = Modifier.clickable { onToggleExpanded() },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )
        }
    }
}

/**
 * 弹幕源选择器组件，以FlowRow布局显示所有可用的弹幕源。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DanmakuSourceChips(
    fetchResults: List<DanmakuFetchResultWithConfig>,
    selectedSources: Set<DanmakuServiceId>,
    onToggleSource: (DanmakuServiceId) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fetchResults.forEach { result ->
            DanmakuSourceChip(
                result = result,
                isSelected = result.serviceId in selectedSources,
                onToggle = { onToggleSource(result.serviceId) },
                onManualMatch = { onManualMatch(result.serviceId) },
            )
        }
    }
}

/**
 * 单个弹幕源选择芯片组件，显示弹幕源名称和状态。
 */
@Composable
private fun DanmakuSourceChip(
    result: DanmakuFetchResultWithConfig,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onManualMatch: () -> Unit,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val isAnimeko = result.serviceId == DanmakuServiceId.Animeko
    val isFuzzyMatch = !result.matchInfo.method.isExactMatch()

    Box {
        FilterChip(
            selected = isSelected,
            onClick = onToggle,
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(renderDanmakuServiceId(result.serviceId))

                    if (!isAnimeko) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多选项",
                            modifier = Modifier.clickable { showDropdown = true },
                        )
                    }
                }
            },
            colors = if (isSelected && isFuzzyMatch) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
        )

        if (showDropdown && !isAnimeko) {
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (isSelected) "禁用" else "启用") },
                    onClick = {
                        onToggle()
                        showDropdown = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("重新匹配") },
                    onClick = {
                        onManualMatch()
                        showDropdown = false
                    },
                )
            }
        }
    }
}

private fun DanmakuMatchMethod.isExactMatch(): Boolean {
    return when (this) {
        is DanmakuMatchMethod.Exact,
        is DanmakuMatchMethod.ExactId -> true

        else -> false
    }
}

private fun renderDanmakuServiceId(serviceId: DanmakuServiceId): String = when (serviceId) {
    DanmakuServiceId.Animeko -> "Animeko"
    DanmakuServiceId.AcFun -> "AcFun"
    DanmakuServiceId.Baha -> "Baha"
    DanmakuServiceId.Bilibili -> "哔哩哔哩"
    DanmakuServiceId.Dandanplay -> "弹弹play"
    DanmakuServiceId.Tucao -> "Tucao"
    else -> serviceId.value
}

/**
 * 弹幕列表项视图组件，显示单条弹幕的详细信息。
 */
@Composable
private fun DanmakuListItemView(danmaku: DanmakuListItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (danmaku.isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = danmaku.content,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(danmaku.timeMillis / 1000 / 60).toInt()}:${
                    (danmaku.timeMillis / 1000 % 60).toInt().toString().padStart(2, '0')
                } · ${renderDanmakuServiceId(danmaku.serviceId)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}