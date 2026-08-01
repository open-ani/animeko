/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HorizontalRule
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_unknown
import me.him188.ani.app.ui.lang.media_selector_filter_clear
import me.him188.ani.app.ui.lang.media_selector_filter_expand
import me.him188.ani.app.ui.lang.media_selector_filter_selected
import me.him188.ani.app.ui.lang.media_selector_view_show_excluded
import me.him188.ani.app.ui.lang.media_source_results_failed
import me.him188.ani.app.ui.lang.media_source_results_rate_limited
import me.him188.ani.app.ui.lang.media_source_results_verify
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.app.ui.settings.rendering.SmallMediaSourceIcon
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.FileSize
import org.jetbrains.compose.resources.stringResource

// ---- TV 聚焦滚动策略 ----

/**
 * TV 聚焦滚动策略: 焦点元素总是吸附到滚动容器顶部 (横向滚动为左缘), 取代默认的
 * "最小滚动露出" —— 默认策略每次滚动距离取决于元素尺寸与当前位置, 遥控器连续导航时
 * 观感为乱跳; 吸附后每按一次方向键滚动一步, 焦点位置恒定可预期.
 *
 * 通过 [LocalBringIntoViewSpec] 对子树内全部滚动容器 (纵向列表与行内横向滚动) 生效.
 * 非焦点驱动的形态原样组合 [content], 零影响.
 *
 * 注意: 本 spec 恒返回非零滚动距离, 列表末尾滚不到容器顶的条目其 bringIntoView
 * 请求永远不"完成" —— 子树内不要 await `BringIntoViewRequester.bringIntoView()`
 * (fire-and-forget / collectLatest 取消兜底的用法安全).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SnapToStartScrollProvider(content: @Composable () -> Unit) {
    if (!LocalAniUiBehavior.current.focusDrivenNavigation) {
        content()
        return
    }
    val marginPx = with(LocalDensity.current) { SNAP_SCROLL_MARGIN.toPx() }
    val spec = remember(marginPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - marginPx
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** 吸附后焦点元素与容器顶部/左缘的留白. */
private val SNAP_SCROLL_MARGIN = 8.dp

/**
 * 选择器内统一的可聚焦容器: 聚焦主题色描边示焦, [selected] 换主题底色
 * (资源卡片/数据源胶囊/筛选胶囊/筛选值单元格/排除开关共用).
 */
@Composable
private fun FocusSelectorSurface(
    onClick: () -> Unit,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = TV_SELECTOR_ITEM_CONTAINER_ALPHA)
        },
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        interactionSource = interactionSource,
    ) {
        content()
    }
}

// ---- 资源条目卡片 (详细模式) ----

/**
 * TV 版资源条目: 整卡单焦点 (确认键选择), 聚焦主题色描边示焦.
 *
 * 替代移动端 [MediaSelectorItem] 的 chip 形态 —— 卡内的大小/分辨率/字幕 chips 与
 * 数据源下拉框各自可聚焦, 遥控器导航会在卡内乱跳; TV 上偏好设置由上方筛选行承担,
 * 卡内信息全部降级为纯文本.
 */
@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
internal fun FocusMediaSelectorItem(
    group: MediaGroup,
    groupState: MediaGroupState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    selected: Boolean,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    val media: Media = group.first.original
    val currentItem = groupState.selectedItem ?: media
    val sourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(currentItem.mediaSourceId)
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    val reasonText = mediaExclusionReasonText(group.exclusionReason)
    val unknownText = stringResource(Lang.cache_unknown)
    val infoText = remember(media, mediaDetailsStrings) {
        buildList {
            media.properties.resolution.takeIf { it.isNotBlank() }?.let(::add)
            if (media.properties.size != FileSize.Zero && media.properties.size != FileSize.Unspecified) {
                add(media.properties.size.toString())
            }
            media.properties.subtitleLanguageIds.forEach { add(renderSubtitleLanguage(it, mediaDetailsStrings)) }
            media.properties.alliance.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ")
    }
    FocusSelectorSurface(
        onClick = { onSelect(currentItem) },
        selected = selected,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                media.originalTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (infoText.isNotEmpty()) {
                Text(
                    infoText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 排除原因 (无字幕/季度不匹配等) 单独一行, 不与资源信息挤在一起
            reasonText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    MediaSourceIcons.location(currentItem.location, currentItem.kind),
                    contentDescription = null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    sourceInfo?.displayName ?: unknownText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDateTime(media.publishedTime, showTime = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 未选中资源卡的容器不透明度 (半透出弹窗底色). */
private const val TV_SELECTOR_ITEM_CONTAINER_ALPHA = 0.5f

// ---- 数据源状态胶囊 (BT / 在线 行) ----

/**
 * TV 版数据源状态条目: 胶囊按钮 (图标 + 名称 + 状态尾标), 聚焦主题色描边示焦.
 * 替代移动端的 [androidx.compose.material3.InputChip] (聚焦指示过弱, 遥控器上看不清落点).
 */
@Composable
internal fun FocusMediaSourceResultChip(
    selected: Boolean,
    onClick: () -> Unit,
    source: MediaSourceResultPresentation,
    modifier: Modifier = Modifier,
) {
    val failedText = stringResource(Lang.media_source_results_failed)
    val rateLimitedText = stringResource(Lang.media_source_results_rate_limited)
    val verifyText = stringResource(Lang.media_source_results_verify)
    FocusSelectorSurface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallMediaSourceIcon(source.info)
            Text(
                source.info.displayName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 状态尾标: 禁用 / 搜索中 / 失败 / 需验证 / 限流 / 结果数
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                when {
                    source.isDisabled -> Icon(
                        Icons.Outlined.HorizontalRule, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    source.isWorking -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)

                    source.isFailedOrAbandoned -> Icon(
                        Icons.Outlined.Close, failedText, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )

                    source.isCaptchaRequired -> Text(verifyText, color = MaterialTheme.colorScheme.error)

                    source.isRateLimited -> Text(rateLimitedText, color = MaterialTheme.colorScheme.tertiary)

                    else -> Text(
                        remember(source.totalCount) { "${source.totalCount}" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---- 筛选器 (分辨率 / 字幕语言 / 字幕组) ----

/**
 * TV 版筛选器入口: 胶囊按钮 (聚焦描边示焦), 点击打开居中网格弹窗选值.
 *
 * 替代移动端 InputChip + DropdownMenu 形态 —— chip 无聚焦视觉, 下拉菜单靠延时
 * 抢焦点不可靠且长列表纵向翻页费劲; 网格弹窗 3 列可一屏看全, 初始焦点落在当前
 * 选中项, 返回键关闭后焦点还给本按钮.
 */
@Composable
internal fun <T : Any> FocusMediaSelectorFilterChip(
    selected: T?,
    allValues: () -> List<T>,
    onSelect: (T) -> Unit,
    onDeselect: (T) -> Unit,
    name: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit,
) {
    val values by remember(allValues) { derivedStateOf(allValues) }
    // 只有一个可选值: 无可筛选, 显示静态胶囊 (不可聚焦, 遥控器直接跳过)
    if (values.size == 1) {
        Surface(
            modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    values.firstOrNull()?.let { label(it) }
                }
            }
        }
        return
    }

    var showDialog by rememberSaveable { mutableStateOf(false) }
    // 弹窗关闭后把焦点还给本按钮 (Dialog 关闭不会自动归还; 初始组合不触发)
    var restoreFocusPending by remember { mutableStateOf(false) }
    val chipFocusRequester = remember { FocusRequester() }
    var chipFocused by remember { mutableStateOf(false) }
    LaunchedEffect(restoreFocusPending) {
        if (restoreFocusPending) {
            resolveFocusRepeatedly(arrived = { chipFocused }) {
                runCatching { chipFocusRequester.requestFocus() }
            }
            restoreFocusPending = false
        }
    }

    FocusSelectorSurface(
        onClick = { showDialog = true },
        selected = selected != null,
        shape = CircleShape,
        modifier = modifier
            .focusRequester(chipFocusRequester)
            .onFocusChanged { chipFocused = it.isFocused },
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                selected?.let { label(it) } ?: name()
            }
            Icon(
                Icons.Default.ArrowDropDown,
                stringResource(Lang.media_selector_filter_expand),
                Modifier.size(18.dp),
            )
        }
    }

    if (showDialog) {
        FilterOptionsGridDialog(
            values = values,
            selected = selected,
            onSelectOption = {
                onSelect(it)
                showDialog = false
                restoreFocusPending = true
            },
            onClear = if (selected != null) {
                {
                    selected.let(onDeselect)
                    showDialog = false
                    restoreFocusPending = true
                }
            } else null,
            onDismissRequest = {
                showDialog = false
                restoreFocusPending = true
            },
            title = name,
            label = label,
        )
    }
}

/**
 * "显示已被排除的资源"开关胶囊: 与筛选胶囊同行同款, 点击切换 —— 启用时主题底色
 * 高亮 + 对勾, 再点恢复. 替代移动端列表底部的 文字+Switch 行 (在长列表末尾, 遥控器够不到).
 */
@Composable
internal fun FocusShowExcludedChip(
    checked: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSelectorSurface(
        onClick = onToggle,
        selected = checked,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Lang.media_selector_view_show_excluded, count),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            if (checked) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 筛选值网格弹窗: 固定 [TV_FILTER_DIALOG_COLUMNS] 列 (长列表一屏看全),
 * 初始焦点落在当前选中项 (未选中落第一项), 已选中时头部附"清除"项.
 */
@Composable
private fun <T : Any> FilterOptionsGridDialog(
    values: List<T>,
    selected: T?,
    onSelectOption: (T) -> Unit,
    onClear: (() -> Unit)?,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    label: @Composable (T) -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // attempts 60: 等弹窗与网格条目组合完成
        resolveFocusRepeatedly(attempts = 60, arrived = { initialFocused }) {
            runCatching { initialFocus.requestFocus() }
        }
    }
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(TV_FILTER_DIALOG_WIDTH_FRACTION)
                .heightIn(max = FILTER_DIALOG_MAX_HEIGHT),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                LazyVerticalGrid(
                    GridCells.Fixed(TV_FILTER_DIALOG_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onClear != null) {
                        item(key = "clear") {
                            FilterOptionCell(
                                selected = false,
                                onClick = onClear,
                            ) {
                                Text(stringResource(Lang.media_selector_filter_clear))
                            }
                        }
                    }
                    items(values.size) { i ->
                        val item = values[i]
                        FilterOptionCell(
                            selected = item == selected,
                            onClick = { onSelectOption(item) },
                            modifier = if (i == initialIndex) {
                                Modifier
                                    .focusRequester(initialFocus)
                                    .onFocusChanged { if (it.isFocused) initialFocused = true }
                            } else Modifier,
                        ) {
                            label(item)
                        }
                    }
                }
            }
        }
    }
}

/** 筛选值单元格: 聚焦描边, 选中主题底色 + 对勾. */
@Composable
private fun FilterOptionCell(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FocusSelectorSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(Modifier.weight(1f)) { content() }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    stringResource(Lang.media_selector_filter_selected),
                    Modifier.size(16.dp),
                )
            }
        }
    }
}

private const val TV_FILTER_DIALOG_COLUMNS = 3
private const val TV_FILTER_DIALOG_WIDTH_FRACTION = 0.6f
private val FILTER_DIALOG_MAX_HEIGHT = 440.dp
