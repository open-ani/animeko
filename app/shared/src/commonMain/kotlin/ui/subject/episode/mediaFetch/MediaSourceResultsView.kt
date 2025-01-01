/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.app.ui.settings.rendering.SmallMediaSourceIcon

@Composable
fun MediaSourceResultsView(
    sourceResults: MediaSourceResultsPresentation,
    mediaSelector: MediaSelectorState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation by mediaSelector.mediaSource.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    MediaSourceResultsView(
        sourceResults = sourceResults,
        sourceSelected = { presentation.finalSelected == it },
        onClickEnabled = {
            scope.launch {
                mediaSelector.mediaSource.preferOrRemove(it).join()
                mediaSelector.removePreferencesUntilFirstCandidate()
            }
        },
        onRefresh,
        modifier,
    )
}

@Composable
fun MediaSourceResultsView(
    sourceResults: MediaSourceResultsPresentation,
    sourceSelected: (String) -> Boolean,
    onClickEnabled: (mediaSourceId: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        var isShowDetails by rememberSaveable { mutableStateOf(false) }
        Row(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { isShowDetails = !isShowDetails },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                remember(
                    sourceResults.anyLoading,
                    sourceResults.enabledSourceCount,
                    sourceResults.totalSourceCount,
                ) {
                    val status = if (sourceResults.anyLoading) "正在查询" else "已查询"
                    "$status ${sourceResults.enabledSourceCount}/${sourceResults.totalSourceCount} 数据源"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )

            var showHelp by remember { mutableStateOf(false) }
            if (showHelp) {
                BasicAlertDialog({ showHelp = false }) {
                    MediaSelectorHelp({ showHelp = false })
                }
            }
            IconButton(onRefresh) {
                Icon(Icons.Outlined.Refresh, "刷新")
            }
            IconButton({ showHelp = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, "帮助")
            }
            val navigator = LocalNavigator.current
            IconButton({ navigator.navigateSettings(SettingsTab.MEDIA_SOURCE) }) {
                Icon(Icons.Outlined.Settings, "设置")
            }

            // TODO: 允许展开的话可能要考虑需要把下面 FlowList 变成 Grid 
//                    IconButton({ isShowDetails = !isShowDetails }) {
//                        if (isShowDetails) {
//                            Icon(Icons.Rounded.UnfoldLess, "展示更少")
//                        } else {
//                            Icon(Icons.Rounded.UnfoldMore, "展示更多")
//                        }
//                    }
        }

        Column(
            Modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val onClick: (MediaSourceResultPresentation) -> Unit = remember(onClickEnabled) {
                { item ->
                    if (item.isDisabled || item.isFailedOrAbandoned) {
                        item.restart()
                    } else {
                        onClickEnabled(item.mediaSourceId)
                    }
                }
            }
            MediaSourceResultsRow(
                isShowDetails,
                sourceResults.btSources,
                sourceSelected = sourceSelected,
                onClick = onClick,
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(MediaSourceIcons.KindBT, null)
                        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                            Box(Modifier.padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                Text("在线", Modifier.alpha(0f)) // 相同宽度
                                Text("BT")
                            }
                        }
                    }
                },
            )
            MediaSourceResultsRow(
                isShowDetails,
                sourceResults.webSources,
                sourceSelected = sourceSelected,
                onClick = onClick,
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(MediaSourceIcons.KindWeb, null)
                        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                            Box(Modifier.padding(top = 2.dp), contentAlignment = Alignment.Center) {
                                Text("在线")
                            }
                        }
                    }
//                            Icon(MediaSourceIcons.Web, null)
//                            Text("在线", Modifier.padding(start = 4.dp))
                },
            )
        }
    }
}

@Composable
private fun MediaSourceResultsRow(
    expanded: Boolean,
    list: List<MediaSourceResultPresentation>,
    sourceSelected: (String) -> Boolean,
    onClick: (MediaSourceResultPresentation) -> Unit,
    label: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                label()
            }
        }

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                for (item in list) {
                    MediaSourceResultCard(
                        sourceSelected(item.mediaSourceId),
                        expanded = true,
                        { onClick(item) },
                        item,
                        Modifier
                            .widthIn(min = 100.dp)
                            .ifThen(item.isDisabled) {
                                alpha(1 - 0.618f)
                            },
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                items(list, key = { it.mediaSourceId }) { item ->
                    MediaSourceResultCard(
                        sourceSelected(item.mediaSourceId),
                        expanded = false,
                        { onClick(item) },
                        item,
                        Modifier
                            .ifThen(item.isDisabled) {
                                alpha(1 - 0.618f)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSourceResultCard(
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    source: MediaSourceResultPresentation,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        OutlinedCard(
            onClick = onClick,
            modifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else CardDefaults.elevatedCardColors().containerColor,
            ),
        ) {
            Column(
                Modifier.padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallMediaSourceIcon(source.info)

                    Text(
                        source.info.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp),
                    )
                }

                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Row(
                        Modifier.heightIn(min = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            source.isDisabled -> {
                                Icon(Icons.Outlined.HorizontalRule, null)
                                Text("点击临时启用")
                            }

                            source.isWorking -> {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp)
                                Text(remember(source.totalCount) { "${source.totalCount}" })
                            }

                            source.isFailedOrAbandoned -> {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                                    Icon(Icons.Outlined.Close, "查询失败")
                                    Text("点击重试")
                                }
                            }

                            else -> {
                                Icon(Icons.Outlined.Check, "查询成功")
                                Text(remember(source.totalCount) { "${source.totalCount}" })
                            }
                        }
                    }
                }

                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    }
                }
            }
        }
    } else {
        InputChip(
            selected,
            onClick,
            label = {
                when {
                    source.isDisabled -> {
                        Icon(Icons.Outlined.HorizontalRule, null)
                    }

                    source.isWorking -> {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp)
                    }

                    source.isFailedOrAbandoned -> {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                            Icon(Icons.Outlined.Close, "查询失败")
                        }
                    }

                    else -> {
                        Text(remember(source.totalCount) { "${source.totalCount}" })
                    }
                }
            },
            modifier = modifier.heightIn(min = 40.dp),
            leadingIcon = {
                SmallMediaSourceIcon(
                    source.info,
                )
            },
        )
    }
}