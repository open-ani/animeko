/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.bangumi.BangumiAutoMergedChange
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_auto_collection_type
import me.him188.ani.app.ui.lang.bangumi_merge_auto_comment
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged_more
import me.him188.ani.app.ui.lang.bangumi_merge_auto_new_collection
import me.him188.ani.app.ui.lang.bangumi_merge_auto_rating
import me.him188.ani.app.ui.lang.bangumi_merge_auto_rating_cleared
import me.him188.ani.app.ui.lang.bangumi_merge_auto_unwatched
import me.him188.ani.app.ui.lang.bangumi_merge_auto_watched
import me.him188.ani.app.ui.lang.bangumi_merge_deleted_collection
import me.him188.ani.app.ui.lang.bangumi_merge_episode
import me.him188.ani.app.ui.lang.bangumi_merge_field_collection
import me.him188.ani.app.ui.lang.bangumi_merge_field_column
import me.him188.ani.app.ui.lang.bangumi_merge_field_comment
import me.him188.ani.app.ui.lang.bangumi_merge_field_rating
import me.him188.ani.app.ui.lang.bangumi_merge_field_status
import me.him188.ani.app.ui.lang.bangumi_merge_from_animeko
import me.him188.ani.app.ui.lang.bangumi_merge_from_bangumi
import me.him188.ani.app.ui.lang.bangumi_merge_no_comment
import me.him188.ani.app.ui.lang.bangumi_merge_no_rating
import me.him188.ani.app.ui.lang.bangumi_merge_no_tags
import me.him188.ani.app.ui.lang.bangumi_merge_pending_count
import me.him188.ani.app.ui.lang.bangumi_merge_private
import me.him188.ani.app.ui.lang.bangumi_merge_public
import me.him188.ani.app.ui.lang.bangumi_merge_resolved
import me.him188.ani.app.ui.lang.bangumi_merge_score
import me.him188.ani.app.ui.lang.bangumi_merge_select_all
import me.him188.ani.app.ui.lang.bangumi_merge_subject_column
import me.him188.ani.app.ui.lang.bangumi_merge_tags
import me.him188.ani.app.ui.lang.bangumi_merge_yesterday
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Bangumi 品牌色, 用于 Bangumi 列头圆点. 独立于主题色, 保持来源辨识度.
 */
private val BangumiBrandColor = Color(0xFFF09199)

/**
 * 冲突列表: 按窗口宽度分派为紧凑卡片列表 / 条目左列表格 / 双栏网格. 列表底部是可展开的自动合并明细.
 */
@Composable
internal fun BangumiMergeConflictList(
    state: BangumiMergeUiState,
    onSelect: (BangumiConflictKey, BangumiMergeSide) -> Unit,
    onSelectAll: (BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    flashRequest: MergeFlashRequest?,
    getTimeNow: () -> Instant,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups
    val now = remember(state.mergeState) { getTimeNow() }
    val timeZone = remember { TimeZone.currentSystemDefault() }

    // 闪烁提示: 点击禁用态"应用合并"后滚动到第一个未决定的条目并高亮.
    // nonce 消费记录放在布局分支之外: 窗口尺寸变化切换分支时, 新分支的
    // LaunchedEffect 会带着旧的 flashRequest 重新执行, 不能重放滚动与高亮.
    var highlightedSubjectId by remember { mutableStateOf<Int?>(null) }
    var consumedFlashNonce by remember { mutableStateOf(-1) }

    if (layoutParams.useTwoColumnGrid) {
        val gridState = rememberLazyStaggeredGridState()
        LaunchedEffect(flashRequest) {
            val request = flashRequest ?: return@LaunchedEffect
            if (request.nonce == consumedFlashNonce) return@LaunchedEffect
            consumedFlashNonce = request.nonce
            // item 0 为列头.
            gridState.animateScrollToItem(1 + request.groupIndex)
            highlightedSubjectId = request.subjectId
            delay(1500)
            highlightedSubjectId = null
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = modifier.testTag(BangumiMergeTestTags.LIST),
            state = gridState,
            contentPadding = PaddingValues(
                horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding,
                vertical = 8.dp,
            ),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "colhead", span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    repeat(2) { index ->
                        MergeTableColumnHeader(
                            onSelectAll = onSelectAll,
                            layoutParams = layoutParams,
                            showSelectAll = index == 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            items(groups.size, key = { groups[it].subjectId }) { index ->
                val group = groups[index]
                MergeTableSubjectCard(
                    group = group,
                    choices = state.choices,
                    onSelect = onSelect,
                    layoutParams = layoutParams,
                    highlighted = highlightedSubjectId == group.subjectId,
                    now = now,
                    timeZone = timeZone,
                )
            }
            if (state.autoMergedTotal > 0) {
                item(key = "automerged", span = StaggeredGridItemSpan.FullLine) {
                    AutoMergedSection(state.autoMerged, state.autoMergedTotal, Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        var headerHeightPx by remember { mutableStateOf(0) }
        LaunchedEffect(flashRequest) {
            val request = flashRequest ?: return@LaunchedEffect
            if (request.nonce == consumedFlashNonce) return@LaunchedEffect
            consumedFlashNonce = request.nonce
            // item 0 为吸顶列头; 负偏移把目标条目滚到吸顶列头下方而不是被它遮住.
            listState.animateScrollToItem(1 + request.groupIndex, scrollOffset = -headerHeightPx)
            highlightedSubjectId = request.subjectId
            delay(1500)
            highlightedSubjectId = null
        }
        MergeLazyColumn(
            onHeaderSizeChanged = { headerHeightPx = it },
            state = state,
            onSelect = onSelect,
            onSelectAll = onSelectAll,
            layoutParams = layoutParams,
            listState = listState,
            highlightedSubjectId = highlightedSubjectId,
            now = now,
            timeZone = timeZone,
            modifier = modifier,
        )
    }
}

@Composable
private fun MergeLazyColumn(
    state: BangumiMergeUiState,
    onSelect: (BangumiConflictKey, BangumiMergeSide) -> Unit,
    onSelectAll: (BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    listState: LazyListState,
    highlightedSubjectId: Int?,
    now: Instant,
    timeZone: TimeZone,
    onHeaderSizeChanged: (heightPx: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups
    val horizontalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding
    LazyColumn(
        modifier = modifier.testTag(BangumiMergeTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickyHeader(key = "colhead") {
            // 吸顶列头需要不透明背景, 避免滚动内容透出.
            Box(
                Modifier
                    .background(AniThemeDefaults.pageContentBackgroundColor)
                    .onSizeChanged { onHeaderSizeChanged(it.height) },
            ) {
                if (layoutParams.useTableLayout) {
                    MergeTableColumnHeader(
                        onSelectAll = onSelectAll,
                        layoutParams = layoutParams,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                } else {
                    MergeCompactColumnHeader(
                        onSelectAll = onSelectAll,
                        layoutParams = layoutParams,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
        items(groups.size, key = { groups[it].subjectId }) { index ->
            val group = groups[index]
            val itemModifier = Modifier.padding(horizontal = horizontalPadding)
            if (layoutParams.useTableLayout) {
                MergeTableSubjectCard(
                    group = group,
                    choices = state.choices,
                    onSelect = onSelect,
                    layoutParams = layoutParams,
                    highlighted = highlightedSubjectId == group.subjectId,
                    now = now,
                    timeZone = timeZone,
                    modifier = itemModifier,
                )
            } else {
                MergeCompactSubjectCard(
                    group = group,
                    choices = state.choices,
                    onSelect = onSelect,
                    layoutParams = layoutParams,
                    highlighted = highlightedSubjectId == group.subjectId,
                    now = now,
                    timeZone = timeZone,
                    modifier = itemModifier,
                )
            }
        }
        if (state.autoMergedTotal > 0) {
            item(key = "automerged") {
                AutoMergedSection(
                    state.autoMerged,
                    state.autoMergedTotal,
                    Modifier.padding(horizontal = horizontalPadding).fillMaxWidth(),
                )
            }
        }
    }
}

// region 列头

/**
 * 紧凑布局吸顶列头: [spacer] [● Animeko ── 全选] [● Bangumi ── 全选].
 */
@Composable
private fun MergeCompactColumnHeader(
    onSelectAll: (BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(layoutParams.fieldColumnWidth))
            MergeColumnHeaderSide(BangumiMergeSide.ANIMEKO, onSelectAll, showSelectAll = true, Modifier.weight(1f))
            MergeColumnHeaderSide(BangumiMergeSide.BANGUMI, onSelectAll, showSelectAll = true, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 表格布局列头: [条目] [字段] [● Animeko ── 全选] [● Bangumi ── 全选].
 */
@Composable
private fun MergeTableColumnHeader(
    onSelectAll: (BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    modifier: Modifier = Modifier,
    showSelectAll: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Lang.bangumi_merge_subject_column),
                Modifier.width(layoutParams.subjectColumnWidth + 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Lang.bangumi_merge_field_column),
                Modifier.width(layoutParams.fieldColumnWidth),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MergeColumnHeaderSide(BangumiMergeSide.ANIMEKO, onSelectAll, showSelectAll, Modifier.weight(1f))
            MergeColumnHeaderSide(BangumiMergeSide.BANGUMI, onSelectAll, showSelectAll, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MergeColumnHeaderSide(
    side: BangumiMergeSide,
    onSelectAll: (BangumiMergeSide) -> Unit,
    showSelectAll: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).background(
                when (side) {
                    BangumiMergeSide.ANIMEKO -> MaterialTheme.colorScheme.primary
                    BangumiMergeSide.BANGUMI -> BangumiBrandColor
                },
                CircleShape,
            ),
        )
        Text(
            when (side) {
                BangumiMergeSide.ANIMEKO -> "Animeko"
                BangumiMergeSide.BANGUMI -> "Bangumi"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        if (showSelectAll) {
            Text(
                stringResource(Lang.bangumi_merge_select_all),
                Modifier
                    .clickable { onSelectAll(side) }
                    .testTag(
                        when (side) {
                            BangumiMergeSide.ANIMEKO -> BangumiMergeTestTags.SELECT_ALL_LOCAL
                            BangumiMergeSide.BANGUMI -> BangumiMergeTestTags.SELECT_ALL_REMOTE
                        },
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// endregion

// region 条目卡片

/**
 * 紧凑布局: 按条目分组的卡片, 分组头压成单行 (标题 + 待选计数/已解决).
 */
@Composable
private fun MergeCompactSubjectCard(
    group: SubjectMergeConflictGroup,
    choices: Map<BangumiConflictKey, BangumiMergeSide>,
    onSelect: (BangumiConflictKey, BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    highlighted: Boolean,
    now: Instant,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    MergeCardSurface(highlighted, modifier.testTag(BangumiMergeTestTags.card(group.subjectId))) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MergeGroupStatus(group, choices)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (conflict in group.conflicts) {
                    MergeConflictRow(
                        conflict = conflict,
                        choices = choices,
                        onSelect = onSelect,
                        layoutParams = layoutParams,
                        now = now,
                        timeZone = timeZone,
                    )
                }
            }
        }
    }
}

/**
 * 表格布局: 条目名占据左侧固定列, 与它的多个字段冲突横向对齐.
 */
@Composable
private fun MergeTableSubjectCard(
    group: SubjectMergeConflictGroup,
    choices: Map<BangumiConflictKey, BangumiMergeSide>,
    onSelect: (BangumiConflictKey, BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    highlighted: Boolean,
    now: Instant,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    MergeCardSurface(highlighted, modifier.testTag(BangumiMergeTestTags.card(group.subjectId))) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.width(layoutParams.subjectColumnWidth),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MergeGroupStatus(group, choices)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (conflict in group.conflicts) {
                    MergeConflictRow(
                        conflict = conflict,
                        choices = choices,
                        onSelect = onSelect,
                        layoutParams = layoutParams,
                        now = now,
                        timeZone = timeZone,
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeCardSurface(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 条目不用带底色的卡片: 页面上只留一种强调色 (选中格) 和一种语义色 (已删除收藏), 条目之间用留白 + 细分隔线分组.
    // 闪烁提示 (点禁用的 "应用合并") 用很淡的主色底短暂标出目标条目.
    val highlightColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
    )
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(highlightColor)) {
            content()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/**
 * 分组头右侧状态: 全部解决 → "✓ 已解决"; 否则 "N 待选".
 */
@Composable
private fun MergeGroupStatus(
    group: SubjectMergeConflictGroup,
    choices: Map<BangumiConflictKey, BangumiMergeSide>,
) {
    val pendingCount = group.conflicts.count { it.key !in choices }
    if (pendingCount == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Check,
                null,
                Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Lang.bangumi_merge_resolved),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            stringResource(Lang.bangumi_merge_pending_count, pendingCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region 冲突行与单元格

/**
 * 一行一个冲突: [字段窄列] [Animeko 单元格] [Bangumi 单元格].
 *
 * 单元格复用 M3 SegmentedButton 的形状语义: 外角 10dp, 内角 4dp, 间隔 2dp.
 */
@Composable
private fun MergeConflictRow(
    conflict: BangumiMergeConflict,
    choices: Map<BangumiConflictKey, BangumiMergeSide>,
    onSelect: (BangumiConflictKey, BangumiMergeSide) -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    now: Instant,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val key = conflict.key
    val choice = choices[key]
    // 长文本 (短评等) 单行截断; 点击字段名或长按单元格展开完整对照.
    var expanded by rememberSaveable(key.subjectId, key.fieldType.name) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .width(layoutParams.fieldColumnWidth)
                .defaultMinSize(minHeight = layoutParams.rowMinHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(start = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                conflict.fieldLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (side in listOf(BangumiMergeSide.ANIMEKO, BangumiMergeSide.BANGUMI)) {
            MergeChoiceCell(
                content = conflict.cellContent(side),
                modifiedAt = conflict.sideModifiedAt(side),
                isNewer = conflict.newerSide == side,
                selected = choice == side,
                ghost = choice != null && choice != side,
                isStart = side == BangumiMergeSide.ANIMEKO,
                layoutParams = layoutParams,
                expanded = expanded,
                onClick = { onSelect(key, side) },
                onToggleExpand = { expanded = !expanded },
                now = now,
                timeZone = timeZone,
                modifier = Modifier.weight(1f).testTag(BangumiMergeTestTags.cell(key, side)),
            )
        }
    }
}

internal data class MergeCellContent(
    val primary: String,
    val secondary: String?,
    val destructive: Boolean,
)

@Composable
private fun MergeChoiceCell(
    content: MergeCellContent,
    modifiedAt: Instant?,
    isNewer: Boolean,
    selected: Boolean,
    ghost: Boolean,
    isStart: Boolean,
    layoutParams: BangumiMergeLayoutParams,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    now: Instant,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    // SegmentedButton 语义: 外角 10dp, 内角 4dp.
    val shape = if (isStart) {
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
    }
    val contentAlpha by animateFloatAsState(if (ghost) 0.5f else 1f)
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(onLongClick = onToggleExpand, onClick = onClick)
            // 无障碍: 一对单选按钮语义, 朗读当前是否选中.
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = shape,
        // 只有选中格有底色; 落选格 (ghost) 不再铺灰底, 只把描边和内容压淡.
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (ghost) 0.5f else 1f))
        },
    ) {
        if (layoutParams.twoLineCell) {
            Column(
                Modifier
                    .defaultMinSize(minHeight = layoutParams.rowMinHeight)
                    .padding(horizontal = 9.dp, vertical = 8.dp)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        MergeCellCheckIcon()
                    }
                    MergeCellPrimaryText(content, expanded, Modifier.weight(1f, fill = false))
                    if (content.secondary != null) {
                        Text(
                            content.secondary,
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MergeCellTimeChip(modifiedAt, isNewer, now, timeZone)
            }
        } else {
            Row(
                Modifier
                    .defaultMinSize(minHeight = layoutParams.rowMinHeight)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    MergeCellCheckIcon()
                }
                if (content.secondary != null) {
                    // 评分 + 短评/标签: 评分定宽, 次级文本弹性截断.
                    MergeCellPrimaryText(content, expanded)
                    Text(
                        content.secondary,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // 单值: 值本身弹性截断, 时间芯片保持完整.
                    MergeCellPrimaryText(content, expanded, Modifier.weight(1f))
                }
                MergeCellTimeChip(modifiedAt, isNewer, now, timeZone)
            }
        }
    }
}

@Composable
private fun MergeCellCheckIcon() {
    Icon(
        Icons.Rounded.Check,
        null,
        Modifier.size(13.dp),
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun MergeCellPrimaryText(
    content: MergeCellContent,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        content.primary,
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = if (content.destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = if (expanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 时间戳 (紧凑格式), 较新的一侧带 ● 标记, 免去逐行心算时间.
 */
@Composable
private fun MergeCellTimeChip(
    modifiedAt: Instant?,
    isNewer: Boolean,
    now: Instant,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    if (modifiedAt == null) return
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isNewer) {
            Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        Text(
            formatMergeTime(modifiedAt, now, timeZone, stringResource(Lang.bangumi_merge_yesterday)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region 值渲染

@Composable
private fun UnifiedCollectionType.collectionText(): String = when (this) {
    UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
    UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
    UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
    UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
    UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
    UnifiedCollectionType.NOT_COLLECTED -> stringResource(Lang.bangumi_merge_deleted_collection)
}

@Composable
private fun BangumiMergeConflict.fieldLabel(): String = when (this) {
    is BangumiMergeConflict.Collection ->
        if (animeko.value == UnifiedCollectionType.NOT_COLLECTED || bangumi.value == UnifiedCollectionType.NOT_COLLECTED) {
            stringResource(Lang.bangumi_merge_field_collection)
        } else {
            stringResource(Lang.bangumi_merge_field_status)
        }

    is BangumiMergeConflict.Rating -> stringResource(Lang.bangumi_merge_field_rating)
    is BangumiMergeConflict.Comment -> stringResource(Lang.bangumi_merge_field_comment)
}

/**
 * 单元格内容:
 * - 收藏状态: 直接显示值, "已删除收藏" 为破坏性;
 * - 评分行: 主文本为 "N 分" / "未评分", 次级文本依次拼接不同的短评 / "标签: …" / "私密"|"公开";
 * - 短评行: 主文本为短评.
 */
@Composable
internal fun BangumiMergeConflict.cellContent(side: BangumiMergeSide): MergeCellContent = when (this) {
    is BangumiMergeConflict.Collection -> {
        val value = sideValue(side).value
        MergeCellContent(
            value.collectionText(),
            secondary = null,
            destructive = value == UnifiedCollectionType.NOT_COLLECTED,
        )
    }

    is BangumiMergeConflict.Rating -> {
        val value = sideValue(side).value
        val secondaryParts = buildList {
            if (includesComment) {
                add(value.comment?.let { "“$it”" } ?: stringResource(Lang.bangumi_merge_no_comment))
            }
            if (includesTags) {
                add(
                    if (value.tags.isEmpty()) {
                        stringResource(Lang.bangumi_merge_no_tags)
                    } else {
                        stringResource(Lang.bangumi_merge_tags, value.tags.joinToString(", "))
                    },
                )
            }
            if (includesPrivate) {
                add(stringResource(if (value.isPrivate) Lang.bangumi_merge_private else Lang.bangumi_merge_public))
            }
        }
        MergeCellContent(
            if (value.score == 0) {
                stringResource(Lang.bangumi_merge_no_rating)
            } else {
                stringResource(Lang.bangumi_merge_score, value.score)
            },
            secondary = secondaryParts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            destructive = false,
        )
    }

    is BangumiMergeConflict.Comment -> {
        val value = sideValue(side).value
        MergeCellContent(
            value?.let { "“$it”" } ?: stringResource(Lang.bangumi_merge_no_comment),
            secondary = null,
            destructive = false,
        )
    }
}

// endregion

// region 自动合并明细

/**
 * 上次全量同步自动合并的差异, 收纳为可展开的明细, 默认不占空间但可审计.
 *
 * 服务端最多返回 100 条明细, 超出部分以 "另有 N 项…" 提示.
 */
@Composable
internal fun AutoMergedSection(
    autoMerged: List<BangumiAutoMergedChange>,
    autoMergedTotal: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        Row(
            Modifier
                .clickable { expanded = !expanded }
                .testTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Lang.bangumi_merge_auto_merged, autoMergedTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Rounded.ExpandMore,
                null,
                Modifier.size(15.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (change in autoMerged) {
                    AutoMergedRow(change)
                }
                val more = autoMergedTotal - autoMerged.size
                if (more > 0) {
                    Text(
                        stringResource(Lang.bangumi_merge_auto_merged_more, more),
                        Modifier.testTag(BangumiMergeTestTags.AUTO_MERGED_MORE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoMergedRow(
    change: BangumiAutoMergedChange,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            change.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            change.describeChange(),
            // fill = false: 长短评等长值截断展示, 不把标题和来源挤出屏幕.
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when (change.side) {
                BangumiMergeSide.ANIMEKO -> stringResource(Lang.bangumi_merge_from_animeko)
                BangumiMergeSide.BANGUMI -> stringResource(Lang.bangumi_merge_from_bangumi)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 自动合并项的描述: "新增收藏 · 想看" / "状态 · 在看" / "评分 9" / "短评 “…”" / "观看进度 → EP5" / "取消已看 EP3".
 */
@Composable
internal fun BangumiAutoMergedChange.describeChange(): String = when (fieldType) {
    BangumiConflictFieldType.COLLECTION -> {
        val typeText = collectionType?.collectionText() ?: ""
        if (isNew) {
            stringResource(Lang.bangumi_merge_auto_new_collection, typeText)
        } else {
            stringResource(Lang.bangumi_merge_auto_collection_type, typeText)
        }
    }

    BangumiConflictFieldType.RATING -> {
        val rating = rating
        val comment = rating?.comment?.takeIf { it.isNotBlank() }
        when {
            rating == null -> stringResource(Lang.bangumi_merge_auto_rating_cleared)
            rating.score > 0 -> buildString {
                append(stringResource(Lang.bangumi_merge_auto_rating, rating.score))
                if (comment != null) {
                    append(" · “")
                    append(comment)
                    append("”")
                }
            }

            comment != null -> stringResource(Lang.bangumi_merge_auto_comment, comment)
            else -> stringResource(Lang.bangumi_merge_auto_rating_cleared)
        }
    }

    BangumiConflictFieldType.EPISODE -> {
        val parts = buildList {
            if (watchedEpisodeSorts.isNotEmpty()) {
                add(stringResource(Lang.bangumi_merge_auto_watched, watchedEpisodeSorts.episodeListText()))
            }
            if (unwatchedEpisodeSorts.isNotEmpty()) {
                add(stringResource(Lang.bangumi_merge_auto_unwatched, unwatchedEpisodeSorts.episodeListText()))
            }
        }
        parts.joinToString(" · ")
    }
}

@Composable
private fun List<String>.episodeListText(): String =
    map { stringResource(Lang.bangumi_merge_episode, it) }.joinToString(", ")

// endregion
