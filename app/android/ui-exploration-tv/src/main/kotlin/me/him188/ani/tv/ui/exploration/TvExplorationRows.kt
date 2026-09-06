/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.tv.ui.foundation.focus.TvAnchoredBringIntoViewSpec
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor

/*
 * 探索页 hero 下方的行列表 (atv-architecture.md §7.1 v5): 纵向 LazyColumn, 焦点行始终贴在
 * hero 下方; 「继续观看」是横向锚定行 (焦点卡恒在行首), 「为你推荐」是纵向自适应网格
 * (列数按可用宽算、行内 weight 等分, 手机 GridCells.Adaptive 同语义).
 *
 * 纵向锚定用 Compose 的 BringIntoViewSpec 实现: 焦点落到卡片 → 焦点系统发 bringIntoView →
 * 列把卡对齐顶部 (有行头的行预留行头高度). 纯焦点事件驱动, 无轮询/延时 (§14.4-8).
 */

/** 行内卡片锚点: (行 key, 行内索引). 行间导航/横向行左右移动都以此为送焦目标. */
internal data class TvExplorationCardKey(val rowKey: String, val index: Int) : TvFocusKey

/** 探索页一行的数据规格. [title] null = 网格续行 (无行头). [count] 随 Paging 加载增长. */
internal sealed class TvExplorationRow(val key: String, val title: String?) {
    abstract val count: Int

    /** 继续观看: 横向锚定行. */
    class ContinueWatching(val items: LazyPagingItems<FollowedSubjectInfo>) :
        TvExplorationRow("followed", "继续观看") {
        override val count: Int get() = items.itemCount
    }

    /** 为你推荐网格的第 [rowIndex] 行 ([columns] 列), 只有首行带行头. */
    class RecommendationGrid(
        val items: LazyPagingItems<RecommendedItemInfo>,
        val rowIndex: Int,
        val columns: Int,
    ) : TvExplorationRow("rec-$rowIndex", if (rowIndex == 0) "为你推荐" else null) {
        val start: Int = rowIndex * columns
        override val count: Int get() = (items.itemCount - start).coerceIn(0, columns)
    }
}

/** TMDB w1280 backdrop 降到卡片档 (w780). */
internal fun tmdbBackdropCardUrl(url: String): String = url.replace("/t/p/w1280/", "/t/p/w780/")

/**
 * 探索页一行: [行头] + 内容. 焦点接线在此:
 * - 继续观看 (横向锚定行): ← 前一张先滚行让其重新组合再送焦 (锚定后前一张已滚出组合,
 *   空间搜索找不到), 首卡按左不消费 (交给侧栏); → 直接送焦 (后一张已组合), BringIntoView 对齐行首
 * - 网格行: ←→ 交给空间搜索 (整行都在组合中)
 * - ↑/↓ 一律显式 [onNavigateVertical] (上一行已滚出组合; 行 0 按上回 hero)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvExplorationRowItem(
    row: TvExplorationRow,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    rowStates: SnapshotStateMap<String, LazyListState>,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (row: TvExplorationRow, subject: TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
    onNavigateVertical: (delta: Int, fromIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalNav = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val current = focusedIndexByRow[row.key] ?: 0
        when (event.key) {
            Key.DirectionUp -> {
                onNavigateVertical(-1, current)
                true
            }

            Key.DirectionDown -> {
                onNavigateVertical(1, current)
                true
            }

            else -> false
        }
    }
    Column(modifier.fillMaxWidth()) {
        if (row.title != null) {
            Box(
                Modifier.height(TvExplorationDefaults.RowHeaderHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tvHeroContentColor(),
                )
            }
        }
        when (row) {
            is TvExplorationRow.ContinueWatching -> TvContinueWatchingRow(
                row, media, onIntent, focus, rowStates, focusedIndexByRow, onCardFocused, onClickSubject,
                modifier = verticalNav,
            )

            is TvExplorationRow.RecommendationGrid -> TvRecommendationGridRow(
                row, media, onIntent, focus, focusedIndexByRow, onCardFocused, onClickSubject,
                modifier = verticalNav,
            )
        }
    }
}

/** 继续观看: 横向锚定 LazyRow, 横向位置随 route 返回/列表回收保留 (rememberSaveable). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvContinueWatchingRow(
    row: TvExplorationRow.ContinueWatching,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    rowStates: SnapshotStateMap<String, LazyListState>,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (row: TvExplorationRow, subject: TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    SideEffect { if (rowStates[row.key] !== rowState) rowStates[row.key] = rowState }
    val scope = rememberCoroutineScope()
    val startAligned = remember { TvAnchoredBringIntoViewSpec() }

    fun moveTo(target: Int, scrollFirst: Boolean) {
        focusedIndexByRow[row.key] = target
        if (scrollFirst) scope.launch { rowState.animateScrollToItem(target) }
        focus.request(TvExplorationCardKey(row.key, target))
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides startAligned) {
        LazyRow(
            state = rowState,
            modifier = modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val current = focusedIndexByRow[row.key] ?: rowState.firstVisibleItemIndex
                    when (event.key) {
                        Key.DirectionLeft -> if (current > 0) {
                            moveTo(current - 1, scrollFirst = true)
                            true
                        } else {
                            false
                        }

                        Key.DirectionRight -> {
                            if (current + 1 < row.count) moveTo(current + 1, scrollFirst = false)
                            true
                        }

                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
            contentPadding = PaddingValues(end = TvPageDefaults.EndPadding),
        ) {
            items(
                row.count,
                key = { row.items.peek(it)?.subjectInfo?.subjectId ?: it },
            ) { index ->
                val info = row.items[index] ?: return@items
                val subject = TvHeroSubject(
                    info.subjectInfo.subjectId,
                    info.subjectInfo.displayName,
                    info.subjectInfo.imageLarge,
                )
                TvExplorationCard(
                    subject = subject,
                    memoryId = "followed-${subject.subjectId}",
                    row = row,
                    index = index,
                    media = media,
                    onIntent = onIntent,
                    focus = focus,
                    focusedIndexByRow = focusedIndexByRow,
                    onCardFocused = onCardFocused,
                    onClickSubject = onClickSubject,
                    collection = info.subjectCollectionInfo,
                    overline = renderContinueWatchingOverline(info.subjectProgressInfo.continueWatchingStatus),
                )
            }
        }
    }
}

/**
 * 为你推荐网格的一行: 行内 weight 等分拉伸 (不会出现固定宽度下最右侧最后一项被压缩),
 * 尾行空位 Spacer 占住.
 */
@Composable
private fun TvRecommendationGridRow(
    row: TvExplorationRow.RecommendationGrid,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (row: TvExplorationRow, subject: TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(end = TvPageDefaults.EndPadding),
        horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
    ) {
        repeat(row.columns) { column ->
            val itemIndex = row.start + column
            val rec = if (itemIndex < row.items.itemCount) {
                row.items[itemIndex] as? RecommendedSubjectInfo
            } else {
                null
            }
            if (rec != null) {
                val subject = TvHeroSubject(rec.bangumiId, rec.nameCn, rec.imageLarge)
                TvExplorationCard(
                    subject = subject,
                    memoryId = "rec-${subject.subjectId}",
                    row = row,
                    index = column,
                    media = media,
                    onIntent = onIntent,
                    focus = focus,
                    focusedIndexByRow = focusedIndexByRow,
                    onCardFocused = onCardFocused,
                    onClickSubject = onClickSubject,
                    width = null,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 一张卡: 横图取 TMDB backdrop (卡片档), 缺图退化海报裁切; 聚焦上报行内索引 + hero 目标. */
@Composable
private fun TvExplorationCard(
    subject: TvHeroSubject,
    memoryId: String,
    row: TvExplorationRow,
    index: Int,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    focus: TvFocusScope,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (row: TvExplorationRow, subject: TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
    width: Dp? = TvLandscapeCardDefaults.Width,
    modifier: Modifier = Modifier,
    overline: String? = null,
    collection: SubjectCollectionInfo? = null,
) {
    LaunchedEffect(subject.subjectId, collection) {
        onIntent(TvExplorationIntent.CardVisible(subject.subjectId, collection))
    }
    val backdrop = media.backdropCache[subject.subjectId]
    TvLandscapeCard(
        imageUrl = backdrop?.let(::tmdbBackdropCardUrl) ?: subject.imageUrl,
        title = subject.title,
        overline = overline,
        onClick = { onClickSubject(subject) },
        onFocused = {
            focusedIndexByRow[row.key] = index
            onCardFocused(row, subject)
        },
        memoryId = memoryId,
        width = width,
        modifier = modifier.tvFocusAnchor(focus, TvExplorationCardKey(row.key, index)),
    )
}

/**
 * 继续观看卡的进度小字 (语义同手机 SubjectProgressState.buttonText, 10-foot 精简版):
 * 待看下一话 → 「继续 · 第 N 话」; 已追到最新 → 「已看到第 N 话」; 一话未看 → 「开始观看」.
 */
private fun renderContinueWatchingOverline(status: ContinueWatchingStatus): String = when (status) {
    is ContinueWatchingStatus.Continue -> "继续 · 第 ${status.episodeEp ?: status.episodeSort ?: "?"} 话"
    is ContinueWatchingStatus.Watched -> "已看到第 ${status.episodeEp ?: status.episodeSort ?: "?"} 话"
    ContinueWatchingStatus.Start -> "开始观看"
    is ContinueWatchingStatus.NotOnAir -> "未开播"
    ContinueWatchingStatus.Done -> "已看完"
}
