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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.a
import me.him188.ani.danmaku.api.DanmakuServiceId
import org.jetbrains.compose.resources.painterResource

/**
 * 弹幕列表区域组件，提供弹幕源选择和弹幕列表显示功能。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListSection(
    state: DanmakuListState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
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
                        // 弹幕源chips
                        if (state.sourceItems.isNotEmpty()) {
                            DanmakuSourceChips(
                                sourceItems = state.sourceItems,
                                onToggleSource = onSetEnabled,
                                onManualMatch = onManualMatch,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }

                        // 弹幕列表
                        if (state.danmakuItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator()
                                } else {
                                    Text(
                                        text = if (state.isEmpty) "暂无弹幕数据" else "没有符合筛选条件的弹幕",
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
                                    items = state.danmakuItems,
                                    key = { it.randomId.toString() },
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
    sourceItems: List<DanmakuSourceItem>,
    onToggleSource: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sourceItems.forEach { sourceItem ->
            DanmakuSourceChip(
                sourceItem = sourceItem,
                onToggle = { onToggleSource(sourceItem.serviceId, !sourceItem.enabled) },
                onManualMatch = { onManualMatch(sourceItem.serviceId) },
            )
        }
    }
}

/**
 * 单个弹幕源选择 Chip 组件，显示弹幕源图标和弹幕数量。
 */
@Composable
private fun DanmakuSourceChip(
    sourceItem: DanmakuSourceItem,
    onToggle: () -> Unit,
    onManualMatch: () -> Unit,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val isAnimeko = sourceItem.serviceId == DanmakuServiceId.Animeko

    Box {
        FilterChip(
            selected = sourceItem.enabled,
            onClick = onToggle,
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isAnimeko) 4.dp else (-4).dp),
                ) {
                    Text(if (sourceItem.count == 0) renderDanmakuServiceId(sourceItem.serviceId) else "${sourceItem.count}")

                    if (!isAnimeko) {
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = "更多选项",
                            modifier = Modifier
                                .offset(x = 8.dp)
                                .clickable { showDropdown = true },
                        )
                    }
                }
            },
            leadingIcon = {
                DanmakuServiceIcon(
                    serviceId = sourceItem.serviceId,
                    size = 24,
                )
            },
            colors = if (sourceItem.enabled && sourceItem.isFuzzyMatch) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DanmakuServiceIcon(
                            serviceId = sourceItem.serviceId,
                            size = 24,
                        )
                        Text(
                            text = renderDanmakuServiceId(sourceItem.serviceId),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                HorizontalDivider()

                // 操作菜单项
                DropdownMenuItem(
                    text = { Text(if (sourceItem.enabled) "禁用" else "启用") },
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

@Composable
private fun DanmakuServiceIcon(
    serviceId: DanmakuServiceId,
    size: Int,
    modifier: Modifier = Modifier,
) {
    when (serviceId) {
        DanmakuServiceId.Animeko -> {
            Image(
                painter = painterResource(Res.drawable.a),
                contentDescription = renderDanmakuServiceId(serviceId),
                modifier = modifier
                    .size(size.dp)
                    .clip(CircleShape),
            )
        }

        else -> {
            val text = getDanmakuServiceIconInfo(serviceId)
            Box(
                modifier = modifier
                    .size(size.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = (size * 0.6).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
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
 * 弹幕源的显示文字
 */
@Composable
private fun getDanmakuServiceIconInfo(serviceId: DanmakuServiceId): String {
    return when (serviceId) {
        DanmakuServiceId.Bilibili -> "哔"
        DanmakuServiceId.Dandanplay -> "弹"
        DanmakuServiceId.AcFun -> "Ac"
        DanmakuServiceId.Baha -> "巴"
        DanmakuServiceId.Tucao -> "TC"
        else -> "?"
    }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${(danmaku.timeMillis / 1000 / 60).toInt()}:${
                        (danmaku.timeMillis / 1000 % 60).toInt().toString().padStart(2, '0')
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DanmakuServiceIcon(
                    serviceId = danmaku.serviceId,
                    size = 24,
                )
            }
        }
    }
}
