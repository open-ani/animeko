/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_filter
import me.him188.ani.app.ui.lang.media_selector_bt_filter_any
import me.him188.ani.app.ui.lang.media_selector_bt_filter_done
import me.him188.ani.app.ui.lang.media_selector_bt_filter_reset
import me.him188.ani.app.ui.lang.media_selector_bt_source_all
import me.him188.ani.app.ui.lang.media_selector_bt_source_count
import me.him188.ani.app.ui.lang.media_selector_bt_source_failed
import me.him188.ani.app.ui.lang.media_selector_bt_source_querying
import me.him188.ani.app.ui.lang.media_selector_filter_alliance
import me.him188.ani.app.ui.lang.media_selector_filter_resolution
import me.him188.ani.app.ui.lang.media_selector_filter_subtitle
import me.him188.ani.app.ui.lang.media_selector_retry
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.mediafetch.MediaPreferenceItemState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultPresentation
import me.him188.ani.app.ui.settings.rendering.SmallMediaSourceIcon
import org.jetbrains.compose.resources.stringResource

/**
 * 紧凑列表两块面板共用的页面内底部面板: 自画 scrim (点击 = [onDismiss]) + 底部对齐、顶部圆角的 Surface, 铺在 [BtResourcesPage] 的根 Box 上, 不是 ModalBottomSheet.
 * ModalBottomSheet 在 Android 上是独立 window, 不继承全屏播放器已隐藏的系统栏, 放在全屏布局层里会把状态栏 / 导航栏拉回来;
 * 页面内叠层在全屏纯布局层、窄屏详情页的 ModalBottomSheet、桌面 Dialog 里行为一致, 也没有 sheet 叠 sheet 时返回键与 scrim 的归属问题.
 * 返回键由 [BtResourcesPage] 统一处理. [visible] 翻转为 false 时播放退出动画, 调用方在动画期间保持组合.
 * Surface 高度由 `weight(1f, fill = false)` 限制在顶部 56dp 留白之下, 内容用 verticalScroll 时有界.
 */
@Composable
private fun BtInlineSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrimColor = BottomSheetDefaults.ScrimColor
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            ) {
                drawRect(color = scrimColor)
            }
            Column(Modifier.fillMaxSize().padding(top = 56.dp), verticalArrangement = Arrangement.Bottom) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .animateEnterExit(enter = slideInVertically { it }, exit = slideOutVertically { it })
                        // Surface 上的点击不落到 scrim
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .testTag(sheetTestTag),
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BottomSheetDefaults.DragHandle()
                        }
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 手机的数据源面板: 首行「全部数据源 N 条」, 之后每个 BT 源一行 (图标 + 名称 + 状态 + 单选).
 * 禁用源灰显, 点击 = [onRestartSource]; 查询失败的源带重试.
 *
 * @param visible 见 [BtInlineSheet]; 调用方始终组合本函数, 由它自己播放进出动画.
 * @param onSelect 选中的 mediaSourceId; null = 全部数据源. 面板随之关闭.
 */
@Composable
internal fun BtSourceSheet(
    visible: Boolean,
    presentation: BtListPresentation,
    sourceResults: MediaSourceResultListPresentation,
    onSelect: (mediaSourceId: String?) -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BtInlineSheet(visible, onDismiss, BtResourcesPageTestTags.SOURCE_SHEET, modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Lang.media_selector_sources),
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BtSourceAllRow(
                    presentation,
                    selected = presentation.sourceFilter == null,
                    onClick = { onSelect(null); onDismiss() },
                )
                for (source in sourceResults.btSources) {
                    BtSourceRow(
                        source, presentation,
                        selected = presentation.sourceFilter == source.mediaSourceId,
                        onClick = {
                            if (source.isDisabled) {
                                onRestartSource(source.instanceId)
                            } else {
                                onSelect(source.mediaSourceId)
                                onDismiss()
                            }
                        },
                        onRestartSource = { onRestartSource(source.instanceId) },
                    )
                }
            }
        }
    }
}

/**
 * 表格模式的数据源下拉 chip: 标签 = 选中源名或「数据源」, 菜单项与面板同一渲染.
 */
@Composable
internal fun BtSourceDropdownChip(
    presentation: BtListPresentation,
    sourceResults: MediaSourceResultListPresentation,
    onSelect: (mediaSourceId: String?) -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        BtSourceChip(
            presentation, sourceResults,
            onClick = { expanded = true },
            Modifier.testTag(BtResourcesPageTestTags.SOURCE_CHIP),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PlatformPopupProperties(clippingEnabled = false),
        ) {
            BtSourceAllRow(
                presentation,
                selected = presentation.sourceFilter == null,
                onClick = { onSelect(null); expanded = false },
                asMenuItem = true,
            )
            for (source in sourceResults.btSources) {
                BtSourceRow(
                    source, presentation,
                    selected = presentation.sourceFilter == source.mediaSourceId,
                    onClick = {
                        if (source.isDisabled) {
                            onRestartSource(source.instanceId)
                        } else {
                            onSelect(source.mediaSourceId)
                        }
                        expanded = false
                    },
                    onRestartSource = { onRestartSource(source.instanceId) },
                    asMenuItem = true,
                )
            }
        }
    }
}

/**
 * 筛选行里的数据源 chip: 选了具体源时为选中态且显示源名, 否则「数据源」.
 */
@Composable
internal fun BtSourceChip(
    presentation: BtListPresentation,
    sourceResults: MediaSourceResultListPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSource = presentation.sourceFilter?.let { id -> sourceResults.btSources.firstOrNull { it.mediaSourceId == id } }
    val label = selectedSource?.info?.displayName ?: stringResource(Lang.media_selector_sources)
    FilterChip(
        selected = selectedSource != null,
        onClick = onClick,
        label = { Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
    )
}

@Composable
private fun BtSourceAllRow(
    presentation: BtListPresentation,
    selected: Boolean,
    onClick: () -> Unit,
    asMenuItem: Boolean = false,
) {
    BtSourceItem(
        icon = { Icon(Icons.Rounded.AllInclusive, contentDescription = null, Modifier.size(24.dp)) },
        title = stringResource(Lang.media_selector_bt_source_all, presentation.totalCount),
        status = null,
        selected = selected,
        enabled = true,
        onClick = onClick,
        retry = null,
        asMenuItem = asMenuItem,
        modifier = Modifier,
    )
}

@Composable
private fun BtSourceRow(
    source: MediaSourceResultPresentation,
    presentation: BtListPresentation,
    selected: Boolean,
    onClick: () -> Unit,
    onRestartSource: () -> Unit,
    asMenuItem: Boolean = false,
) {
    val status: @Composable () -> Unit = {
        when {
            source.isWorking -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(Lang.media_selector_bt_source_querying))
            }

            source.isFailedOrAbandoned -> Text(
                stringResource(Lang.media_selector_bt_source_failed),
                color = MaterialTheme.colorScheme.error,
            )

            else -> Text(
                stringResource(Lang.media_selector_bt_source_count, presentation.sourceCounts[source.mediaSourceId] ?: 0),
            )
        }
    }
    BtSourceItem(
        icon = { SmallMediaSourceIcon(source.info) },
        title = source.info.displayName,
        status = status,
        selected = selected,
        enabled = !source.isDisabled,
        onClick = onClick,
        retry = if (source.isFailedOrAbandoned) {
            {
                TextButton(onClick = onRestartSource) {
                    Text(stringResource(Lang.media_selector_retry))
                }
            }
        } else null,
        asMenuItem = asMenuItem,
        modifier = Modifier.testTag(BtResourcesPageTestTags.sourceItem(source.mediaSourceId)),
    )
}

/**
 * 面板行与菜单项共用的内容: 图标 + 名称 / 状态 + (重试) + 单选.
 * [enabled] 为 false 时灰显但仍可点击 (点击 = 重启该源).
 */
@Composable
private fun BtSourceItem(
    icon: @Composable () -> Unit,
    title: String,
    status: (@Composable () -> Unit)?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    retry: (@Composable () -> Unit)?,
    asMenuItem: Boolean,
    modifier: Modifier,
) {
    val alpha = if (enabled) 1f else 0.38f
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.alpha(alpha)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            status()
                        }
                    }
                }
            }
        }
    }
    val trailing: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            retry?.invoke()
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
    if (asMenuItem) {
        DropdownMenuItem(
            text = { Box(Modifier.alpha(alpha)) { text() } },
            onClick = onClick,
            modifier = modifier,
            leadingIcon = { Box(Modifier.alpha(alpha)) { icon() } },
            trailingIcon = trailing,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .height(56.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(alpha)) { icon() }
            Box(Modifier.weight(1f).alpha(alpha)) { text() }
            trailing()
        }
    }
}

/**
 * 手机的筛选面板: 分辨率 / 字幕 / 字幕组三组 FilterChip 单选, 每组首项「不限」= removePreference;
 * 右上「重置」三项各 removePreference; 底部「完成」关闭.
 *
 * @param visible 见 [BtInlineSheet]; 调用方始终组合本函数, 由它自己播放进出动画.
 */
@Composable
internal fun BtFilterSheet(
    visible: Boolean,
    presentation: BtListPresentation,
    resolution: MediaPreferenceItemState<String>,
    subtitleLanguageId: MediaPreferenceItemState<String>,
    alliance: MediaPreferenceItemState<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    BtInlineSheet(visible, onDismiss, BtResourcesPageTestTags.FILTER_SHEET, modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Lang.media_selector_bt_filter), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch {
                            resolution.removePreference()
                            subtitleLanguageId.removePreference()
                            alliance.removePreference()
                        }
                    },
                    Modifier.testTag(BtResourcesPageTestTags.FILTER_RESET),
                ) {
                    Text(stringResource(Lang.media_selector_bt_filter_reset))
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BtFilterGroup(
                    title = stringResource(Lang.media_selector_filter_resolution),
                    values = presentation.availableResolutions,
                    selected = presentation.resolution,
                    onSelect = { value -> scope.launch { if (value == null) resolution.removePreference() else resolution.prefer(value) } },
                )
                BtFilterGroup(
                    title = stringResource(Lang.media_selector_filter_subtitle),
                    values = presentation.availableSubtitleLanguageIds,
                    selected = presentation.subtitleLanguageId,
                    onSelect = { value ->
                        scope.launch { if (value == null) subtitleLanguageId.removePreference() else subtitleLanguageId.prefer(value) }
                    },
                    label = { renderSubtitleLanguage(it, mediaDetailsStrings) },
                )
                BtFilterGroup(
                    title = stringResource(Lang.media_selector_filter_alliance),
                    values = presentation.availableAlliances,
                    selected = presentation.alliance,
                    onSelect = { value -> scope.launch { if (value == null) alliance.removePreference() else alliance.prefer(value) } },
                )
            }
            Button(
                onClick = onDismiss,
                Modifier.fillMaxWidth().testTag(BtResourcesPageTestTags.FILTER_DONE),
            ) {
                Text(stringResource(Lang.media_selector_bt_filter_done))
            }
        }
    }
}

/**
 * 一组单选 chip. 当前偏好不在候选里时追加显示, 让用户能看到并取消它.
 */
@Composable
private fun BtFilterGroup(
    title: String,
    values: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    label: (String) -> String = { it },
) {
    val shown = if (selected != null && selected !in values) values + selected else values
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Lang.media_selector_bt_filter_any)) },
            )
            for (value in shown) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
