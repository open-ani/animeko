/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.a
import me.him188.ani.app.ui.foundation.lists.LazyListVerticalScrollbar
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_collapse
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_error_empty
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_error_malformed
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_error_unknown
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_error_unsupported
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_file
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_reading
import me.him188.ani.app.ui.lang.subject_episode_danmaku_import_success
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty_filtered
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_title
import me.him188.ani.app.ui.lang.subject_episode_danmaku_rematch
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_baha_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_bilibili_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_dandanplay_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_time_shift_item
import me.him188.ani.app.ui.lang.subject_episode_disable
import me.him188.ani.app.ui.lang.subject_episode_enable
import me.him188.ani.app.ui.lang.subject_episode_expand
import me.him188.ani.app.ui.lang.subject_episode_more_options
import me.him188.ani.app.ui.subject.episode.details.components.formatDanmakuShiftMillis
import me.him188.ani.app.ui.subject.episode.details.components.renderDanmakuServiceId
import me.him188.ani.danmaku.api.DanmakuFileParseException
import me.him188.ani.danmaku.api.DanmakuFileParser
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    onAdjustShift: (DanmakuServiceId) -> Unit,
    onImportDanmakuFile: (fileName: String, danmaku: List<DanmakuInfo>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listTitleText = stringResource(Lang.subject_episode_danmaku_list_title)
    val collapseText = stringResource(Lang.subject_episode_collapse)
    val expandText = stringResource(Lang.subject_episode_expand)

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
                    DanmakuListContent(
                        state = state,
                        onSetEnabled = onSetEnabled,
                        onManualMatch = onManualMatch,
                        onAdjustShift = onAdjustShift,
                        onImportDanmakuFile = onImportDanmakuFile,
                        modifier = Modifier.padding(top = 64.dp),
                    )
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
                    Text(listTitleText)
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.FeaturedPlayList, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) collapseText else expandText,
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
 * 弹幕列表的实际内容，不包含可收起标题栏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListContent(
    state: DanmakuListState,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    onAdjustShift: (DanmakuServiceId) -> Unit,
    onImportDanmakuFile: (fileName: String, danmaku: List<DanmakuInfo>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptyText = if (state.isEmpty) {
        stringResource(Lang.subject_episode_danmaku_list_empty)
    } else {
        stringResource(Lang.subject_episode_danmaku_list_empty_filtered)
    }

    Column(modifier = modifier) {
        // 弹幕源 chips. 即使一个源都没有, 也要显示导入按钮.
        DanmakuSourceChips(
            sourceItems = state.sourceItems,
            onToggleSource = onSetEnabled,
            onManualMatch = onManualMatch,
            onAdjustShift = onAdjustShift,
            onImportDanmakuFile = onImportDanmakuFile,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

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
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    items(
                        items = state.danmakuItems,
                        key = { it.randomId.toString() },
                    ) { danmaku ->
                        DanmakuListItemView(danmaku)
                    }
                }
                LazyListVerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                        .placeScrollbarToAbsoluteRight(),
                )
            }
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
    onAdjustShift: (DanmakuServiceId) -> Unit,
    onImportDanmakuFile: (fileName: String, danmaku: List<DanmakuInfo>) -> Unit,
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
                onAdjustShift = { onAdjustShift(sourceItem.serviceId) },
            )
        }

        DanmakuFileImportChip(onImportDanmakuFile)
    }
}

private val logger = logger("DanmakuListSection")

/**
 * 导入结果, 就地显示在「导入弹幕文件…」按钮旁边, 不需要额外的 snackbar.
 */
private sealed interface DanmakuImportResult {
    class Success(val count: Int) : DanmakuImportResult
    class Failure(val message: String) : DanmakuImportResult
}

/**
 * 「导入弹幕文件…」按钮. 选择文件, 读取, 解析都在这里完成, 只把解析好的弹幕交给上层.
 */
@Composable
private fun DanmakuFileImportChip(
    onImportDanmakuFile: (fileName: String, danmaku: List<DanmakuInfo>) -> Unit,
) {
    val importFileText = stringResource(Lang.subject_episode_danmaku_import_file)
    val readingText = stringResource(Lang.subject_episode_danmaku_import_reading)

    val unsupportedText = stringResource(Lang.subject_episode_danmaku_import_error_unsupported)
    val malformedText = stringResource(Lang.subject_episode_danmaku_import_error_malformed)
    val noDanmakuText = stringResource(Lang.subject_episode_danmaku_import_error_empty)
    val unknownErrorText = stringResource(Lang.subject_episode_danmaku_import_error_unknown)

    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DanmakuImportResult?>(null) }

    val onImportDanmakuFileUpdated by rememberUpdatedState(onImportDanmakuFile)

    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("xml", "json")),
    ) { file ->
        if (file == null) return@rememberFilePickerLauncher
        result = null
        importing = true
        scope.launch {
            try {
                val bytes = file.readBytes()
                val parsed = withContext(Dispatchers.Default) { DanmakuFileParser.parse(bytes) }
                onImportDanmakuFileUpdated(file.name, parsed.list)
                result = DanmakuImportResult.Success(parsed.list.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DanmakuFileParseException) {
                logger.warn(e) { "Failed to parse danmaku file ${file.name}" }
                result = DanmakuImportResult.Failure(
                    when (e.reason) {
                        DanmakuFileParseException.Reason.UnsupportedFormat -> unsupportedText
                        DanmakuFileParseException.Reason.Malformed -> malformedText
                        DanmakuFileParseException.Reason.NoDanmaku -> noDanmakuText
                    },
                )
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to read danmaku file ${file.name}" }
                result = DanmakuImportResult.Failure(unknownErrorText)
            } finally {
                importing = false
            }
        }
    }

    AssistChip(
        onClick = { filePicker.launch() },
        enabled = !importing,
        label = { Text(if (importing) readingText else importFileText) },
        leadingIcon = {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Outlined.FileOpen, contentDescription = null)
            }
        },
    )

    result?.let {
        Text(
            text = when (it) {
                is DanmakuImportResult.Success ->
                    stringResource(Lang.subject_episode_danmaku_import_success, it.count)

                is DanmakuImportResult.Failure -> it.message
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (it) {
                is DanmakuImportResult.Success -> MaterialTheme.colorScheme.onSurfaceVariant
                is DanmakuImportResult.Failure -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
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
    onAdjustShift: () -> Unit,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val isAnimeko = sourceItem.serviceId == DanmakuServiceId.Animeko
    // Animeko 和本地导入的文件都没有"重新匹配"的概念
    val canRematch = !isAnimeko && sourceItem.serviceId != DanmakuServiceId.LocalFile
    val moreOptionsText = stringResource(Lang.subject_episode_more_options)
    val disableText = stringResource(Lang.subject_episode_disable)
    val enableText = stringResource(Lang.subject_episode_enable)
    val rematchText = stringResource(Lang.subject_episode_danmaku_rematch)
    val timeShiftText = stringResource(
        Lang.subject_episode_danmaku_time_shift_item,
        formatDanmakuShiftMillis(sourceItem.shiftMillis),
    )

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

                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = moreOptionsText,
                        modifier = Modifier
                            .offset(x = 8.dp)
                            .clickable { showDropdown = true },
                    )
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

        if (showDropdown) {
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
                    text = { Text(if (sourceItem.enabled) disableText else enableText) },
                    leadingIcon = {
                        Icon(
                            if (sourceItem.enabled) Icons.Outlined.Close else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onToggle()
                        showDropdown = false
                    },
                )
                if (canRematch) {
                    DropdownMenuItem(
                        text = { Text(rematchText) },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        onClick = {
                            onManualMatch()
                            showDropdown = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(timeShiftText) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                    onClick = {
                        onAdjustShift()
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

        DanmakuServiceId.LocalFile -> {
            Box(
                modifier = modifier
                    .size(size.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = renderDanmakuServiceId(serviceId),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((size * 0.65).dp),
                )
            }
        }

        else -> {
            val text = getDanmakuServiceIconInfo(serviceId)
            Box(
                modifier = modifier
                    .size(size.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (size * 0.6).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * 弹幕源的显示文字
 */
@Composable
private fun getDanmakuServiceIconInfo(serviceId: DanmakuServiceId): String {
    return when (serviceId) {
        DanmakuServiceId.Bilibili -> stringResource(Lang.subject_episode_danmaku_service_bilibili_short)
        DanmakuServiceId.Dandanplay -> stringResource(Lang.subject_episode_danmaku_service_dandanplay_short)
        DanmakuServiceId.AcFun -> "Ac"
        DanmakuServiceId.Baha -> stringResource(Lang.subject_episode_danmaku_service_baha_short)
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

/**
 * Places the scrollbar on the visual right edge regardless of layout direction.
 * Because Modifier.align(Alignment.CenterEnd) will perform mirroring based on the layout direction,
 * and we want the scroll bar to always be visually on the right side.
 */
private fun Modifier.placeScrollbarToAbsoluteRight(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val x = (width - placeable.width).coerceAtLeast(0)
        layout(width, height) {
            // use absolute positioning to ignore layout direction mirroring
            placeable.place(x, 0)
        }
    },
)
