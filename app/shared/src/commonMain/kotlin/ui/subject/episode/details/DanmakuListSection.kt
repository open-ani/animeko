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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.domain.episode.DanmakuFetchResultWithConfig
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import kotlin.time.Duration.Companion.milliseconds

data class DanmakuListItem(
    val content: String,
    val timeMillis: Long,
    val serviceId: DanmakuServiceId,
    val isSelf: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListSection(
    danmakuFlow: Flow<List<DanmakuPresentation>>,
    fetchResults: List<DanmakuFetchResultWithConfig>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val danmakuList by danmakuFlow.collectAsState(initial = emptyList())
    var selectedSources by rememberSaveable { mutableStateOf(setOf<DanmakuServiceId>()) }
    
    val availableSources by remember {
        derivedStateOf {
            // 从 fetchResults 获取启用的源
            val sourcesFromFetchResults = fetchResults.filter { it.config.enabled }.map { it.serviceId }
            
            // 从弹幕数据中获取所有可用的 serviceId
            val sourcesFromDanmaku = danmakuList.map { it.danmaku.serviceId }.distinct()
            
            // 如果 fetchResults 为空，则使用弹幕数据中的 serviceId
            val sources = sourcesFromFetchResults.ifEmpty {
                sourcesFromDanmaku
            }
            
            sources
        }
    }
    
    LaunchedEffect(availableSources) {
        if (selectedSources.isEmpty()) {
            selectedSources = availableSources.toSet()
            println("DanmakuListSection.selectedSources initialized: $selectedSources")
        }
    }
    
    val filteredDanmaku by remember {
        derivedStateOf {
            danmakuList
                .mapNotNull { presentation ->
                    val serviceId = inferServiceId(presentation)
                    if (serviceId !in selectedSources) return@mapNotNull null
                    
                    DanmakuListItem(
                        content = presentation.danmaku.text,
                        timeMillis = presentation.danmaku.playTimeMillis,
                        serviceId = serviceId,
                        isSelf = presentation.isSelf,
                    )
                }
                .sortedBy { it.timeMillis }
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column {
            ListItem(
                headlineContent = { 
                    Text(
                        "弹幕列表",
                        style = MaterialTheme.typography.titleMedium,
                    ) 
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.FeaturedPlayList, 
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
                ),
                modifier = Modifier.clickable { onToggleExpanded() }
            )
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // 弹幕源筛选chips
                        if (availableSources.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                availableSources.forEach { serviceId ->
                                    FilterChip(
                                        selected = serviceId in selectedSources,
                                        onClick = {
                                            selectedSources = if (serviceId in selectedSources) {
                                                selectedSources - serviceId
                                            } else {
                                                selectedSources + serviceId
                                            }
                                        },
                                        label = { Text(renderDanmakuServiceId(serviceId)) }
                                    )
                                }
                            }
                        }
                        
                        // 弹幕列表
                        if (filteredDanmaku.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (fetchResults.isEmpty()) {
                                    CircularProgressIndicator()
                                } else {
                                    Text(
                                        text = if (danmakuList.isEmpty()) "暂无弹幕数据" else "没有符合筛选条件的弹幕",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.height(360.dp),
                            ) {
                                items(
                                    items = filteredDanmaku,
                                    key = { "${it.timeMillis}-${it.content.hashCode()}" }
                                ) { danmaku ->
                                    DanmakuListItemView(danmaku)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmakuListItemView(
    danmaku: DanmakuListItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (danmaku.isSelf) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = danmaku.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = renderDanmakuServiceId(danmaku.serviceId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTime(danmaku.timeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun formatTime(millis: Long): String {
    val duration = millis.milliseconds
    val minutes = duration.inWholeMinutes
    val seconds = duration.inWholeSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun inferServiceId(presentation: DanmakuPresentation): DanmakuServiceId {
    return presentation.danmaku.serviceId
}