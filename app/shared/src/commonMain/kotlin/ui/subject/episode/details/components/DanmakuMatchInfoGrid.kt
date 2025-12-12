/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.episode.DanmakuFetchResultWithConfig
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuMatchInfo
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.utils.platform.format1f
import kotlin.math.abs

@Composable
fun DanmakuMatchInfoGrid(
    matchInfos: List<DanmakuFetchResultWithConfig>,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 16.dp,
    item: @Composable FlowRowScope.(result: DanmakuFetchResultWithConfig) -> Unit,
) {
    Column(modifier) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            // infinite measurement
//            LazyVerticalGrid(
//                GridCells.Adaptive(minSize = 120.dp),
//                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
//                verticalArrangement = Arrangement.spacedBy(itemSpacing),
//            ) {
//                items(matchInfos) { info ->
//                    DanmakuMatchInfoView(
//                        info, expanded,
//                        Modifier.weight(1f), // 填满宽度并且让两个卡片有相同高度
//                    )
//                }
//            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                maxItemsInEachRow = 2,
            ) {
                for (result in matchInfos) {
                    item(result)
                }
            }
        }
    }
}

@Composable
fun DanmakuSourceCard(
    info: DanmakuMatchInfo,
    enabled: Boolean,
    showDetails: Boolean,
    shiftMillis: Long,
    onClickSettings: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropdown: @Composable () -> Unit = {},
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(onClick, modifier, colors = colors) {
        Column(
            Modifier.padding(bottom = 16.dp),
        ) {
            ListItem(
                headlineContent = {
                    SelectionContainer {
                        Text(
                            renderDanmakuServiceId(info.serviceId),
                            Modifier.basicMarquee(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                },
                trailingContent = {
                    Box {
                        IconButton(onClickSettings, Modifier.offset(x = 8.dp)) {
                            Icon(Icons.Rounded.MoreVert, "设置 ${renderDanmakuServiceId(info.serviceId)}")
                        }
                        dropdown()
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = colors.containerColor,
                ),
            )
//            Row(Modifier.fillMaxWidth()) {
//                Box(Modifier.weight(1f)) {
//                    SelectionContainer {
//                        Text(
//                            info.providerId,
//                            Modifier.basicMarquee(),
//                            style = MaterialTheme.typography.titleMedium,
//                            maxLines = 1,
//                            textDecoration = if (!enabled) TextDecoration.LineThrough else null,
//                        )
//                    }
//                }
//
//                IconButton(onClickSettings) {
//                    Icon(Icons.Rounded.Settings, "设置 ${info.providerId}")
//                }
//            }

            if (enabled) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Subtitles, "弹幕数量")
                        Text(remember(info.count) { "${info.count}" }, softWrap = false)
                    }
                }

                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DanmakuMatchMethodView(info.method, showDetails)
                    }
                }
            } else {
                ListItem(
                    headlineContent = { Text("已禁用") },
                    leadingContent = {
                        Icon(Icons.Rounded.Close, null)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = colors.containerColor,
                    ),
                )
            }
        }
    }
}

internal fun renderDanmakuServiceId(serviceId: DanmakuServiceId): String = when (serviceId) {
    DanmakuServiceId.Animeko -> "Animeko"
    DanmakuServiceId.AcFun -> "AcFun"
    DanmakuServiceId.Baha -> "Baha"
    DanmakuServiceId.Bilibili -> "哔哩哔哩"
    DanmakuServiceId.Dandanplay -> "弹弹"
    DanmakuServiceId.Tucao -> "Tucao"

    // `else` should not reach in production
    else -> serviceId.value
}

internal fun formatDanmakuShiftMillis(shiftMillis: Long): String {
    if (shiftMillis == 0L) return "0 ms"
    val sign = if (shiftMillis > 0) "+" else "-"
    val absMillis = abs(shiftMillis)
    return if (absMillis >= 1_000) {
        val seconds = absMillis / 1_000.0
        "$sign${String.format1f(seconds)} s"
    } else {
        "$sign$absMillis ms"
    }
}

@Composable
fun DanmakuSourceSettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    enabled: Boolean,
    onClickChange: () -> Unit,
    onSetEnabled: (enabled: Boolean) -> Unit,
    currentShiftMillis: Long,
    onClickAdjustShift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded, onDismissRequest, modifier) {
        DropdownMenuItem(
            text = { Text("更换") },
            onClick = {
                onClickChange()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(if (enabled) "禁用" else "启用") },
            onClick = {
                onSetEnabled(!enabled)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text("时间校准 (${formatDanmakuShiftMillis(currentShiftMillis)})") },
            leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
            onClick = {
                onClickAdjustShift()
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun DanmakuMatchMethodView(
    method: DanmakuMatchMethod,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (method) {
            is DanmakuMatchMethod.Exact -> {
                ExactMatch()
                if (showDetails) {
                    SelectionContainer {
                        Text(method.subjectTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                    SelectionContainer {
                        Text(method.episodeTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                }
            }

            is DanmakuMatchMethod.ExactSubjectFuzzyEpisode -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.tertiary) {
                        Icon(Icons.Outlined.QuestionMark, null)
                        Text("半模糊匹配", softWrap = false)
                    }
                }
                if (showDetails) {
                    SelectionContainer {
                        Text(method.subjectTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                    SelectionContainer {
                        Text(method.episodeTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                }
            }

            is DanmakuMatchMethod.Fuzzy -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.tertiary) {
                        Icon(Icons.Outlined.QuestionMark, null)
                        Text("模糊匹配", softWrap = false)
                    }
                }
                if (showDetails) {
                    SelectionContainer {
                        Text(method.subjectTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                    SelectionContainer {
                        Text(method.episodeTitle, Modifier.basicMarquee(), softWrap = false)
                    }
                }
            }

            is DanmakuMatchMethod.ExactId -> {
                ExactMatch()
                if (showDetails) {
                    SelectionContainer {
                        Text(method.subjectId.toString(), Modifier.basicMarquee(), softWrap = false)
                    }
                    SelectionContainer {
                        Text(method.episodeId.toString(), Modifier.basicMarquee(), softWrap = false)
                    }
                }
            }

            is DanmakuMatchMethod.NoMatch -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                        Icon(Icons.Outlined.Close, null)
                        Text("无匹配", softWrap = false)
                    }
                }
            }
        }
    }
}


@Composable
private fun ExactMatch() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            Icon(Icons.Outlined.WorkspacePremium, null)
            Text("精确匹配")
        }
    }
}

@Composable
private fun Failed(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, null)
            content()
        }
    }
}
