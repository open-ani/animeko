/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import me.him188.ani.app.ui.foundation.LocalEpisodeProgressSettings
import me.him188.ani.app.ui.foundation.LongClickProgressFill
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_next_page
import me.him188.ani.app.ui.lang.subject_details_prev_page
import me.him188.ani.app.ui.lang.subject_episode_mark_watched
import me.him188.ani.app.ui.lang.subject_episode_unwatch
import me.him188.ani.app.ui.subject.details.components.EpisodePaging
import me.him188.ani.app.ui.subject.episode.list.EpisodeCellLabel
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.EpisodeStillBackground
import me.him188.ani.app.ui.subject.episode.list.EpisodeStillDefaults
import me.him188.ani.app.ui.subject.episode.list.EpisodeWatchedToggle
import org.jetbrains.compose.resources.stringResource

/**
 * 单个剧集网格单元 (对应 Figma `EpisodeGridItem`).
 *
 * 着色规则见 `docs/subject-details-rewrite/01-decision-algorithms.md` §3:
 * - 容器: 播放中→primaryContainer, 已看(DONE/DROPPED)→surfaceContainerLow, 未看→surfaceContainerHigh
 * - 集号: 播放中→primary, 已看→onSurfaceVariant@60%, 未看→onSurface(LocalContentColor)
 * - 集名: 已看→onSurfaceVariant@60%, 未看→onSurfaceVariant
 *
 * 布局: 集号与集名并排成一行 ([EpisodeCellLabel]) 贴在单元格左下角, 上方留给剧照画面; 无图时同样的位置, 混合覆盖的一排单元格文字对齐.
 * 右上角是已看状态图标 ([EpisodeWatchedToggle]), 点击它与长按单元格一样触发 [onLongClick] 切换已看.
 *
 * 有剧照 ([EpisodeListItem.imageMedium] 非空且 [showImage]) 时剧照铺满单元格作背景 ([EpisodeStillBackground]),
 * 文字改用深色配色前景 ([EpisodeStillDefaults], 已看→70%, 集名→85%), 播放中用 primary 描边与蒙层表示; 单元格尺寸与无图时一致.
 */
@Composable
fun EpisodeGridCell(
    item: EpisodeListItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    showImage: Boolean = true,
) {
    val isWatched = item.isDoneOrDropped
    val interactionSource = remember { MutableInteractionSource() }
    val still = item.imageMedium?.takeIf { showImage }
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val sortColor = when {
        still != null -> if (isWatched) EpisodeStillDefaults.watchedContentColor else EpisodeStillDefaults.contentColor
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> dimmed
        else -> LocalContentColor.current
    }
    val nameColor = when {
        still != null -> if (isWatched) EpisodeStillDefaults.watchedContentColor else EpisodeStillDefaults.secondaryContentColor
        isWatched -> dimmed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val longClickLabel = stringResource(
        if (isWatched) Lang.subject_episode_unwatch else Lang.subject_episode_mark_watched,
    )

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
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = if (still != null && isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box {
            if (still != null) {
                EpisodeStillBackground(
                    imageUrl = still,
                    dimmed = isWatched,
                    highlighted = isPlaying,
                    modifier = Modifier.matchParentSize(),
                )
            }
            LongClickProgressFill(
                interactionSource = interactionSource,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
            )
            EpisodeCellLabel(
                sort = item.sort.toString(),
                name = item.nameCn.ifBlank { item.name },
                sortColor = sortColor,
                nameColor = nameColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                playingIndicator = if (isPlaying) {
                    {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.GraphicEq),
                            contentDescription = null,
                            Modifier.size(16.dp),
                            tint = sortColor,
                        )
                    }
                } else {
                    null
                },
            )
            EpisodeWatchedToggle(
                isWatched = isWatched,
                onToggle = onLongClick,
                tint = nameColor,
                onStill = still != null,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
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
    cellMinWidth: Dp = 128.dp,
    cellSpacing: Dp = 12.dp,
    rowsPerPage: Int = 2,
    header: @Composable (pager: (@Composable () -> Unit)?) -> Unit = { it?.invoke() },
    showImages: Boolean = LocalEpisodeProgressSettings.current.showEpisodeImages,
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

        Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
            header(
                if (paging.isPaged) {
                    {
                        EpisodePager(
                            page = page,
                            pageCount = paging.pageCount,
                            range = range.first + 1..range.last + 1,
                            total = episodes.size,
                            onPrev = { if (page > 0) page-- },
                            onNext = { if (page < paging.pageCount - 1) page++ },
                        )
                    }
                } else {
                    null
                },
            )
            val pageItems = episodes.subList(range.first.coerceIn(0, episodes.size), (range.last + 1).coerceIn(0, episodes.size))
            // 竖向按行排布, 每行 columns 个
            for (row in pageItems.chunked(columns)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                ) {
                    for (item in row) {
                        EpisodeGridCell(
                            item,
                            isPlaying = item.episodeId == currentEpisodeId,
                            onClick = { onEpisodeClick(item) },
                            onLongClick = { onEpisodeLongClick(item) },
                            modifier = Modifier.weight(1f),
                            showImage = showImages,
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
 * 单元 128×72 (16:9), 与 TMDB 剧照比例一致, 裁切最少.
 */
@Composable
fun EpisodesRow(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    cellWidth: Dp = 128.dp,
    cellHeight: Dp = 72.dp,
    cellSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    showImages: Boolean = LocalEpisodeProgressSettings.current.showEpisodeImages,
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
                showImage = showImages,
            )
        }
    }
}
