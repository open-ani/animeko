/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import me.him188.ani.app.ui.foundation.LongClickProgressFill
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_next_page
import me.him188.ani.app.ui.lang.subject_details_prev_page
import me.him188.ani.app.ui.lang.subject_episode_mark_watched
import me.him188.ani.app.ui.lang.subject_episode_unwatch
import me.him188.ani.app.ui.subject.details.components.EpisodePaging
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import org.jetbrains.compose.resources.stringResource
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior

/**
 * 单个剧集网格单元 (对应 Figma `EpisodeGridItem`).
 *
 * 着色规则见 `docs/subject-details-rewrite/01-decision-algorithms.md` §3:
 * - 容器: 播放中→primaryContainer, 已看(DONE/DROPPED)→surfaceContainerLow, 未看→surfaceContainerHigh
 * - 集号: 播放中→primary, 已看→onSurfaceVariant@60%, 未看→onSurface(LocalContentColor)
 * - 集名: 已看→onSurfaceVariant@60%, 未看→onSurfaceVariant
 */
@Composable
fun EpisodeGridCell(
    item: EpisodeListItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val isWatched = item.isDoneOrDropped
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val sortColor = when {
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> dimmed
        else -> LocalContentColor.current
    }
    val nameColor = if (isWatched) dimmed else MaterialTheme.colorScheme.onSurfaceVariant
    val longClickLabel = stringResource(
        if (isWatched) Lang.subject_episode_unwatch else Lang.subject_episode_mark_watched,
    )

    // 聚焦 (遥控器/键盘) 时集名跑马灯滚动展示全文, 未聚焦时保持省略号
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .height(height)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(EPISODE_CARD_CORNER),
        color = containerColor,
    ) {
        Box {
            LongClickProgressFill(
                interactionSource = interactionSource,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
            )
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isPlaying) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.GraphicEq),
                            contentDescription = null,
                            Modifier.height(16.dp).width(16.dp),
                            tint = sortColor,
                        )
                    }
                    Text(
                        item.sort.toString(),
                        color = sortColor,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    item.nameCn.ifBlank { item.name },
                    if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    color = nameColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 桌面 (双栏/三栏) 选集网格: 每页最多 2 行, 每行列数随列宽动态计算.
 * 初始页为含 [currentEpisodeId] 的页.
 *
 * [header] 的 `pager` 参数为分页控件: 超过一页时非 null (定稿: 分页控件替代 header 集数文案),
 * 不足一页时为 null, 调用方应回退显示集数文案.
 */
@Composable
fun PagedEpisodesGrid(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    cellMinWidth: Dp = 96.dp,
    cellSpacing: Dp = 12.dp,
    rowsPerPage: Int = 2,
    header: @Composable (pager: (@Composable () -> Unit)?) -> Unit = { it?.invoke() },
) {
    BoxWithConstraints(modifier) {
        val columns = remember(maxWidth, cellMinWidth, cellSpacing) {
            (((maxWidth + cellSpacing) / (cellMinWidth + cellSpacing)).toInt()).coerceAtLeast(1)
        }
        val capacity = (columns * rowsPerPage).coerceAtLeast(1)
        val paging = remember(episodes.size, capacity) { EpisodePaging(episodes.size, capacity) }
        val currentIndex = remember(episodes, currentEpisodeId) {
            if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
        }
        var page by remember(paging, currentIndex) { mutableStateOf(paging.initialPage(currentIndex)) }
        val range = paging.itemRange(page)

        // 焦点驱动的界面: 翻页会把整页格子销毁重建, 持有焦点的格子消失后焦点会被重置到页面左上角 (翻到边界页时
        // 翻页按钮变 disabled 也会丢焦点). 这里实现遥控器的自然翻页:
        //  - 网格最后一行按"下" -> 下一页并聚焦新页第一格; 第一行按"上" -> 上一页并聚焦末格;
        //  - 用翻页按钮翻到边界页 (按钮即将 disabled) 时, 把焦点移到格子上, 避免焦点悬空.
        val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
        val firstCellFocus = remember { FocusRequester() }
        val lastCellFocus = remember { FocusRequester() }
        // 0 = 不移动焦点; 1 = 翻页后聚焦第一格; 2 = 翻页后聚焦最后一格
        var pendingFocus by remember { mutableStateOf(0) }
        LaunchedEffect(page, pendingFocus) {
            if (pendingFocus == 0) return@LaunchedEffect
            withFrameNanos { } // 等新页格子布局完成
            runCatching {
                (if (pendingFocus == 1) firstCellFocus else lastCellFocus).requestFocus()
            }
            pendingFocus = 0
        }

        Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
            header(
                if (paging.isPaged) {
                    {
                        EpisodePager(
                            page = page,
                            pageCount = paging.pageCount,
                            range = range.first + 1..range.last + 1,
                            total = episodes.size,
                            onPrev = {
                                if (page > 0) {
                                    // 翻到第一页后按钮将变 disabled, 焦点会悬空, 移到格子上
                                    if (focusDriven && page - 1 == 0) pendingFocus = 1
                                    page--
                                }
                            },
                            onNext = {
                                if (page < paging.pageCount - 1) {
                                    if (focusDriven && page + 1 == paging.pageCount - 1) pendingFocus = 1
                                    page++
                                }
                            },
                        )
                    }
                } else {
                    null
                },
            )
            val pageItems = episodes.subList(range.first.coerceIn(0, episodes.size), (range.last + 1).coerceIn(0, episodes.size))
            // 竖向按行排布, 每行 columns 个
            val rows = pageItems.chunked(columns)
            for ((rowIndex, row) in rows.withIndex()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                ) {
                    for ((colIndex, item) in row.withIndex()) {
                        val isFirstCell = rowIndex == 0 && colIndex == 0
                        val isLastCell = rowIndex == rows.lastIndex && colIndex == row.lastIndex
                        EpisodeGridCell(
                            item,
                            isPlaying = item.episodeId == currentEpisodeId,
                            onClick = { onEpisodeClick(item) },
                            onLongClick = { onEpisodeLongClick(item) },
                            modifier = Modifier.weight(1f)
                                .then(if (isFirstCell) Modifier.focusRequester(firstCellFocus) else Modifier)
                                .then(if (isLastCell) Modifier.focusRequester(lastCellFocus) else Modifier)
                                .then(
                                    if (!focusDriven || !paging.isPaged) Modifier
                                    else Modifier.onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when {
                                            event.key == Key.DirectionDown &&
                                                    rowIndex == rows.lastIndex && page < paging.pageCount - 1 -> {
                                                pendingFocus = 1
                                                page++
                                                true
                                            }

                                            event.key == Key.DirectionUp &&
                                                    rowIndex == 0 && page > 0 -> {
                                                pendingFocus = 2
                                                page--
                                                true
                                            }

                                            else -> false
                                        }
                                    },
                                ),
                        )
                    }
                    // 补齐末行空位, 保持等宽
                    repeat(columns - row.size) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodePager(
    page: Int,
    pageCount: Int,
    range: IntRange,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onPrev, enabled = page > 0) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(Lang.subject_details_prev_page),
            )
        }
        Text(
            "${range.first} – ${range.last} / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onNext, enabled = page < pageCount - 1) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(Lang.subject_details_next_page),
            )
        }
    }
}

/**
 * 手机 (compact) 选集: LazyRow 横滑不分页, 自动滚至当前集.
 *
 * 单元尺寸对齐 Figma `EpisodeCard` (1594:1024): 高 64, 约 16:10.
 */
@Composable
fun EpisodesRow(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    cellWidth: Dp = 104.dp,
    cellHeight: Dp = 64.dp,
    cellSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(episodes, currentEpisodeId) {
        if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }
    LazyRow(
        modifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        contentPadding = contentPadding,
    ) {
        items(episodes, key = { it.episodeId }) { item ->
            EpisodeGridCell(
                item,
                isPlaying = item.episodeId == currentEpisodeId,
                onClick = { onEpisodeClick(item) },
                onLongClick = { onEpisodeLongClick(item) },
                modifier = Modifier.width(cellWidth),
                height = cellHeight,
            )
        }
    }
}
