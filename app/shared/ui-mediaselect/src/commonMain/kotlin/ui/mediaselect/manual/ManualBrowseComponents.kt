/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_load_failed
import me.him188.ani.app.ui.lang.media_selector_manual_captcha_unsupported
import me.him188.ani.app.ui.lang.media_selector_manual_default_channel
import me.him188.ani.app.ui.lang.media_selector_manual_episodes_count
import me.him188.ani.app.ui.lang.media_selector_manual_no_result
import me.him188.ani.app.ui.lang.media_selector_manual_no_sources
import me.him188.ani.app.ui.lang.media_selector_manual_remember_selection
import me.him188.ani.app.ui.lang.media_selector_manual_result_count
import me.him188.ani.app.ui.lang.media_selector_retry
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceTierTag
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import org.jetbrains.compose.resources.stringResource

/**
 * 「数据源」「线路」「共 N 条结果」这类小标签.
 */
@Composable
internal fun ManualSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 源选择框: 带「数据源」标签的只读框显示当前源, 点开下拉菜单竖向列出所有源 (图标 + 名称 + tier), 当前源打勾. 无源时显示提示文本.
 *
 * 源的数量没有上限, 而手动查找常在 300–400dp 宽的侧边栏里: 横排时一行只露出两三个源, 鼠标也难以横向滚动.
 * 竖排的菜单能一次看到更多源, 并用滚轮翻动.
 *
 * @param isPlaceholder 状态尚未就绪 ([ManualBrowsePresentation.isPlaceholder]): 空列表只是还没发射, 画一个空的选择框占位, 不显示「没有支持浏览的数据源」.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualSourceSelector(
    sources: List<ManualBrowseSource>,
    selectedSourceId: String?,
    onSelect: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    if (sources.isEmpty() && !isPlaceholder) {
        Text(
            stringResource(Lang.media_selector_manual_no_sources),
            modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedSource = sources.firstOrNull { it.instanceId == selectedSourceId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && sources.isNotEmpty() },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedSource?.info?.displayName.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(ManualBrowsePageTestTags.SOURCE_SELECTOR),
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(Lang.media_selector_sources)) },
            leadingIcon = selectedSource?.let { source -> { ManualSourceIcon(source) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (source in sources) {
                ManualSourceMenuItem(
                    source,
                    selected = source.instanceId == selectedSourceId,
                    onClick = {
                        expanded = false
                        onSelect(source.instanceId)
                    },
                )
            }
        }
    }
}

/**
 * 选中项用 secondaryContainer 底色 + 对勾; tier 标签与对勾占固定的尾部位置, 各行的标签上下对齐.
 */
@Composable
private fun ManualSourceMenuItem(
    source: ManualBrowseSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(source.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier = Modifier
            .ifThen(selected) { background(colorScheme.secondaryContainer) }
            .semantics { this.selected = selected }
            .testTag(ManualBrowsePageTestTags.sourceItem(source.instanceId)),
        leadingIcon = { ManualSourceIcon(source) },
        trailingIcon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                source.info.tier?.let { tier ->
                    MediaSourceTierTag(
                        tier,
                        containerColor = if (selected) colorScheme.surfaceContainerLowest else colorScheme.secondaryContainer,
                    )
                }
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                }
            }
        },
        colors = if (selected) {
            MenuDefaults.itemColors(
                textColor = colorScheme.onSecondaryContainer,
                trailingIconColor = colorScheme.onSecondaryContainer,
            )
        } else {
            MenuDefaults.itemColors()
        },
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
    )
}

@Composable
private fun ManualSourceIcon(source: ManualBrowseSource) {
    MediaSourceIcon(source.info, Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall))
}

/**
 * 结果列表三态 + 「共 N 条结果」标签. 调用方给 [modifier] 加 `weight(1f)`.
 * [openedSubject] 对应的行高亮 (双栏版式里表示右栏正在显示它).
 */
@Composable
internal fun ManualResultsList(
    results: ManualLoadState<List<BrowseSubject>>,
    openedSubject: BrowseSubject?,
    onOpen: (BrowseSubject) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (results) {
        ManualLoadState.Idle -> Box(modifier)
        ManualLoadState.Loading -> ManualLoadingBox(modifier)
        is ManualLoadState.Failed -> ManualFailedBox(results, onRetry, modifier)
        is ManualLoadState.Success -> Column(modifier) {
            ManualSectionLabel(
                stringResource(Lang.media_selector_manual_result_count, results.value.size),
                Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp),
            )
            if (results.value.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Lang.media_selector_manual_no_result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(results.value) { index, subject ->
                        val opened = subject == openedSubject
                        ListItem(
                            headlineContent = {
                                Text(subject.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpen(subject) }
                                .testTag(ManualBrowsePageTestTags.result(index)),
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            colors = ListItemDefaults.colors(
                                // Unspecified 会被 ListItem 解析为主题 surface (一张浅色卡片), 透明才是跟随宿主容器的平铺行.
                                containerColor = if (opened) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ManualLoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * 错误文本 + 重试; 验证码不受支持时换文案且无重试.
 */
@Composable
internal fun ManualFailedBox(
    failed: ManualLoadState.Failed,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (failed.captchaUnsupported) {
                stringResource(Lang.media_selector_manual_captcha_unsupported)
            } else {
                stringResource(Lang.media_selector_load_failed)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (!failed.captchaUnsupported) {
            TextButton(onRetry, Modifier.testTag(ManualBrowsePageTestTags.RETRY)) {
                Text(stringResource(Lang.media_selector_retry))
            }
        }
    }
}

/**
 * 只有一条没有名字的线路时站点没有线路概念, 不显示线路行.
 */
internal fun shouldShowChannelRow(channels: List<BrowseChannel>): Boolean =
    !(channels.size == 1 && channels.single().name == null)

@Composable
internal fun BrowseChannel.displayLabel(): String =
    label ?: name ?: stringResource(Lang.media_selector_manual_default_channel)

@Composable
internal fun ManualChannelChips(
    channels: List<BrowseChannel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
) {
    LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
    ) {
        itemsIndexed(channels) { index, channel ->
            InputChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(channel.displayLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(ManualBrowsePageTestTags.channelChip(index)),
            )
        }
    }
}

/**
 * 剧集网格. 头部整行: 左「剧集 N 项」, 右「记住选择」+ 开关. 点一项 = [onClick] (直接播放). 调用方给 [modifier] 加 `weight(1f)`.
 *
 * @param rememberSelection 开关的值; null 时不显示开关.
 */
@Composable
internal fun ManualEpisodeGrid(
    episodes: List<BrowseEpisode>,
    selectedIndex: Int?,
    onClick: (Int) -> Unit,
    rememberSelection: Boolean?,
    onRememberSelectionChange: (Boolean) -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ManualSectionLabel(
                    stringResource(Lang.media_selector_manual_episodes_count, episodes.size),
                    Modifier.weight(1f),
                )
                if (rememberSelection != null) {
                    Row(
                        Modifier
                            .toggleable(
                                value = rememberSelection,
                                role = Role.Switch,
                                onValueChange = onRememberSelectionChange,
                            )
                            .testTag(ManualBrowsePageTestTags.REMEMBER_SWITCH),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Lang.media_selector_manual_remember_selection),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(checked = rememberSelection, onCheckedChange = null)
                    }
                }
            }
        }
        itemsIndexed(episodes) { index, episode ->
            ManualEpisodeButton(
                episode.name,
                selected = index == selectedIndex,
                onClick = { onClick(index) },
                Modifier.testTag(ManualBrowsePageTestTags.episode(index)),
            )
        }
    }
}

/**
 * 44dp 高圆角 10 的按钮; 选中 = primaryContainer 填充 + 2dp primary 边框, 未选中 = 1dp outline 描边.
 */
@Composable
private fun ManualEpisodeButton(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
