/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_load_failed
import me.him188.ani.app.ui.lang.media_selector_manual_captcha_unsupported
import me.him188.ani.app.ui.lang.media_selector_manual_default_channel
import me.him188.ani.app.ui.lang.media_selector_manual_episodes_count
import me.him188.ani.app.ui.lang.media_selector_manual_no_result
import me.him188.ani.app.ui.lang.media_selector_manual_no_sources
import me.him188.ani.app.ui.lang.media_selector_manual_play_as
import me.him188.ani.app.ui.lang.media_selector_manual_play_as_next
import me.him188.ani.app.ui.lang.media_selector_manual_play_remember
import me.him188.ani.app.ui.lang.media_selector_manual_play_temporary
import me.him188.ani.app.ui.lang.media_selector_manual_play_title
import me.him188.ani.app.ui.lang.media_selector_manual_result_count
import me.him188.ani.app.ui.lang.media_selector_retry
import me.him188.ani.app.ui.lang.media_selector_search_hint
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
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
 * 源 chips 行: 单选, 水平可滚动, 带 20dp 源图标. 无源时显示提示文本.
 *
 * @param isPlaceholder 状态尚未就绪 ([ManualBrowsePresentation.isPlaceholder]): 空列表只是还没发射, 画一个与 chip 行等高的占位, 不显示「没有支持浏览的数据源」.
 */
@Composable
internal fun ManualSourceChips(
    sources: List<ManualBrowseSource>,
    selectedSourceId: String?,
    onSelect: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    isPlaceholder: Boolean = false,
) {
    if (sources.isEmpty() && isPlaceholder) {
        Spacer(modifier.height(InputChipDefaults.Height))
        return
    }
    if (sources.isEmpty()) {
        Text(
            stringResource(Lang.media_selector_manual_no_sources),
            modifier.padding(contentPadding),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sources, key = { it.instanceId }) { source ->
            InputChip(
                selected = source.instanceId == selectedSourceId,
                onClick = { onSelect(source.instanceId) },
                label = { Text(source.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(ManualBrowsePageTestTags.sourceChip(source.instanceId)),
                avatar = {
                    MediaSourceIcon(source.info, Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall))
                },
            )
        }
    }
}

/**
 * 搜索框. 调用方传入的 [modifier] 先固定宽度, 这里再固定 48dp 高, 之后才轮到 [SearchBarDefaults.InputField] 自带的
 * `sizeIn(minWidth = 360.dp, minHeight = 56.dp)`, 侧边栏 268–368dp 的内容宽度才不会被撑破.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualSearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBarDefaults.InputField(
        query = keyword,
        onQueryChange = onKeywordChange,
        onSearch = { onSearch() },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .testTag(ManualBrowsePageTestTags.SEARCH_FIELD),
        placeholder = { Text(stringResource(Lang.media_selector_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
    )
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
 * 剧集网格, 头部整行放「剧集 N 项」. 调用方给 [modifier] 加 `weight(1f)`.
 */
@Composable
internal fun ManualEpisodeGrid(
    episodes: List<BrowseEpisode>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
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
            ManualSectionLabel(stringResource(Lang.media_selector_manual_episodes_count, episodes.size))
        }
        itemsIndexed(episodes) { index, episode ->
            ManualEpisodeButton(
                episode.name,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
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

internal val ManualBrowsePresentation.canPlay: Boolean
    get() = selectedEpisode != null && target != null && !isPlaying

/**
 * 「作为第 25 话播放，下一话播放 SP」/「作为第 25 话播放」; 未选剧集或会话未就绪时为空.
 */
@Composable
internal fun playSubtext(presentation: ManualBrowsePresentation): String {
    val target = presentation.target ?: return ""
    if (presentation.selectedEpisode == null) return ""
    val next = presentation.nextEpisodeName
    return if (next != null) {
        stringResource(Lang.media_selector_manual_play_as_next, target.episodeSortText, next)
    } else {
        stringResource(Lang.media_selector_manual_play_as, target.episodeSortText)
    }
}

/**
 * 播放确认对话框: 页面内叠层 (半透明 scrim + 居中 Surface), 外观与 M3 AlertDialog 相同 (28dp 圆角、surfaceContainerHigh、24dp 内边距,
 * titleLarge 标题「播放「name」」、bodyMedium 正文 [playSubtext]、右下角文本按钮「仅临时播放，不记忆」+ 填充按钮「播放并记住」).
 * 宽度 = min(容器宽 − 48dp, 360dp); 两个按钮放不下一行时换行、每行右对齐 (与 AlertDialog 的按钮行相同).
 *
 * 不用平台 Dialog: 全屏播放器里本页画在视频区的布局层内, Android 的 Dialog 是独立 window, 弹出会把已隐藏的系统栏拉回来 (与 BtInlineSheet 同理).
 * 可见性 = [ManualBrowsePresentation.confirmingEpisodeIndex] != null; 调用方始终组合本函数, 由它播放进出动画.
 * 点 scrim / 返回键 = [onDismiss]; 按钮 enabled = [canPlay] (isPlaying 时禁用).
 * 返回键: 本函数在页面版式之后组合, 对话框打开时它的 BackHandler 先于堆叠第二页的返回拦截.
 */
@Composable
internal fun ManualPlayConfirmDialog(
    presentation: ManualBrowsePresentation,
    onPlay: (remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = presentation.confirmingEpisodeIndex != null
    BackHandler(enabled = visible, onBack = onDismiss)
    val scrimColor = BottomSheetDefaults.ScrimColor
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .testTag(ManualBrowsePageTestTags.CONFIRM_SCRIM),
            ) {
                drawRect(color = scrimColor)
            }
            Surface(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    // Surface 上的点击不落到 scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .testTag(ManualBrowsePageTestTags.CONFIRM_DIALOG),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                ManualPlayConfirmContent(presentation, onPlay, Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun ManualPlayConfirmContent(
    presentation: ManualBrowsePresentation,
    onPlay: (remember: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = presentation.canPlay
    Column(modifier) {
        Text(
            presentation.selectedEpisode?.let { stringResource(Lang.media_selector_manual_play_title, it.name) }.orEmpty(),
            Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            color = AlertDialogDefaults.titleContentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            playSubtext(presentation),
            Modifier.padding(bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AlertDialogDefaults.textContentColor,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = { onPlay(false) },
                Modifier.testTag(ManualBrowsePageTestTags.PLAY_TEMPORARY),
                enabled = enabled,
            ) {
                Text(stringResource(Lang.media_selector_manual_play_temporary))
            }
            Button(
                onClick = { onPlay(true) },
                Modifier.testTag(ManualBrowsePageTestTags.PLAY_REMEMBER),
                enabled = enabled,
            ) {
                Text(stringResource(Lang.media_selector_manual_play_remember))
            }
        }
    }
}
