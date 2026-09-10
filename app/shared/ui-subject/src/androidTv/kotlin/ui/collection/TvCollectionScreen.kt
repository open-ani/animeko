/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.subject.collection.COLLECTION_TABS_SORTED
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.TvGridFocusState
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.rememberTvGridFocus
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.focus.tvGridEdgeSwitchKeys
import me.him188.ani.leanback.ui.foundation.focus.tvGridFocusItem
import me.him188.ani.leanback.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvPosterCard

/** 追番页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvCollectionFocus : TvFocusKey {
    /** 当前选中的分类 tab (进页初始焦点; 网格按上/按返回的回归目标). 锚点随选中迁移. */
    CurrentTab,

}

/**
 * TV 追番页 (atv-architecture.md §7.4):
 * 顶部 TabRow (聚焦即选中 + 数量角标) + Adaptive 网格.
 *
 * 状态层复用手机 UserCollectionsViewModel/UserCollectionsState (D3): 每 tab 独立缓存的
 * LazyPagingItems、登录变更自动刷新. 每分类的网格滚动位置由 UI 保存.
 * TV ViewModel 复用共享状态，通过 Intent 选择分类和打开条目。
 */
@Composable
fun TvCollectionScreen(
    state: TvCollectionUiState,
    onIntent: (TvCollectionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = state.counts
    val selectedTabIndex = state.selectedTabIndex
    val items = state.items
    val gridStates = COLLECTION_TABS_SORTED.map { type -> key(type) { rememberLazyGridState() } }

    // 统一焦点框架: 进页初始焦点落当前选中 tab; tab 行按下键直达网格首卡
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvCollectionFocus.CurrentTab)
    // 边缘横向切 tab / "对应位置"送焦 (框架原语, 协议见 TvFocusGrid.kt)
    val gridFocus = rememberTvGridFocus(focus)

    // 焦点在网格内时按返回: 直接回当前分类 tab (再按一次返回才交给壳回探索页)
    var gridHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = gridHasFocus) {
        focus.request(TvCollectionFocus.CurrentTab)
    }

    TvCollectionPageLayout(
        focus = focus,
        tabRow = {
            TvCollectionTabRow(
                selectedTabIndex = selectedTabIndex,
                counts = counts,
                focus = focus,
                onEnterGrid = { gridFocus.focusItem(0) },
                onTabFocused = { index ->
                    // Navigation can temporarily focus the first tab while restoring this
                    // page. Only user navigation may change the selected category; an edge
                    // switch also keeps its destination frozen until the new grid is ready.
                    if (focus.userNavGeneration > 0 && !gridFocus.switching && selectedTabIndex != index) {
                        onIntent(TvCollectionIntent.SelectTab(index))
                    }
                },
            )
        },
        modifier = modifier,
    ) {
        if (items.itemCount == 0) {
            TvCollectionEmptyPlaceholder(
                items = items,
                edgeSwitchInFlight = gridFocus.switching,
                onReturnFocusToTab = {
                    gridFocus.cancel()
                    focus.request(TvCollectionFocus.CurrentTab)
                },
            )
        } else {
            TvCollectionGrid(
                items = items,
                gridState = gridStates[selectedTabIndex],
                focus = focus,
                gridFocus = gridFocus,
                onClickSubject = { onIntent(TvCollectionIntent.OpenSubject(it)) },
                hasAdjacentTab = { direction ->
                    if (direction < 0) state.hasPreviousTab else state.hasNextTab
                },
                onSwitchTab = { direction ->
                    onIntent(TvCollectionIntent.SwitchTab(direction))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    // 网格上缘按上: 直达当前分类 tab (空间搜索会落到几何最近的 tab)
                    .tvFocusExit(focus, FocusDirection.Up to TvCollectionFocus.CurrentTab),
            )
        }
    }
}

/**
 * 追番页骨架 (对齐手机 CollectionPageLayout 的 slot 模式):
 * 统一焦点接线 + 顶部 [tabRow] + 下方 [content] (网格或空态).
 */
@Composable
private fun TvCollectionPageLayout(
    focus: TvFocusScope,
    tabRow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .tvFocusNavSignal(focus)
            .padding(top = TvCollectionDefaults.TopPadding),
    ) {
        tabRow()
        content()
    }
}

/** 分类 TabRow: 聚焦即选中 (选中判定在 [onTabFocused], 切换在途冻结) + 数量角标. */
@Composable
private fun TvCollectionTabRow(
    selectedTabIndex: Int,
    counts: SubjectCollectionCounts?,
    focus: TvFocusScope,
    onEnterGrid: () -> Unit,
    onTabFocused: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.padding(start = TvCollectionDefaults.TabRowStartPadding),
    ) {
        COLLECTION_TABS_SORTED.forEachIndexed { index, type ->
            Tab(
                selected = selectedTabIndex == index,
                onFocus = { onTabFocused(index) },
                modifier = Modifier
                    .then(
                        // 锚点挂在"当前选中"的 tab 上, 随选中迁移: 网格按上/按返回
                        // 都回到当前分类, 而不是几何最近的 tab
                        if (index == selectedTabIndex) {
                            Modifier.tvFocusAnchor(focus, TvCollectionFocus.CurrentTab)
                        } else Modifier,
                    )
                    // The first card may have been recycled after scrolling. Let the grid
                    // compose it before requesting focus instead of using a detached anchor.
                    .tvFocusHotkey(focus, Key.DirectionDown, onEnterGrid),
            ) {
                val count = counts?.getCount(type)
                Text(
                    text = type.displayText() + (count?.let { " $it" } ?: ""),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * 空列表占位. 边缘切进来的相邻 tab 是空列表时: 别让焦点悬空, 归还当前 tab.
 * 判据是分页 LoadState 事件 (刷新完成且确无数据), 不是延时猜测 (§14.4-8);
 * 数据只是没加载完时本效应挂起等待, 网格出现即取消, 不会误触.
 */
@Composable
private fun TvCollectionEmptyPlaceholder(
    items: LazyPagingItems<SubjectCollectionInfo>,
    edgeSwitchInFlight: Boolean,
    onReturnFocusToTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(edgeSwitchInFlight, items) {
        if (edgeSwitchInFlight) {
            snapshotFlow {
                items.itemCount == 0 && items.loadState.isCollectionRefreshComplete()
            }.first { it }
            onReturnFocusToTab()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "这里空空如也，去探索页找些番剧吧",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 收藏网格: Adaptive 海报卡网格 + 框架网格焦点接线 (边缘切 tab / 对应位置送焦). */
@Composable
private fun TvCollectionGrid(
    items: LazyPagingItems<SubjectCollectionInfo>,
    gridState: LazyGridState,
    focus: TvFocusScope,
    gridFocus: TvGridFocusState,
    onClickSubject: (SubjectCollectionInfo) -> Unit,
    hasAdjacentTab: (direction: Int) -> Boolean,
    onSwitchTab: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    gridFocus.SendFocusEffect(gridState, itemCount = { items.itemCount })
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TvPageDefaults.PosterGridCellMinWidth),
        state = gridState,
        modifier = modifier.tvGridEdgeSwitchKeys(
            state = gridFocus,
            gridState = gridState,
            itemCount = { items.itemCount },
            hasAdjacent = hasAdjacentTab,
            onSwitch = onSwitchTab,
        ),
        contentPadding = TvPageDefaults.PosterGridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
        verticalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
    ) {
        items(items.itemCount, key = { items.peek(it)?.subjectId ?: it }) { index ->
            val info = items[index] ?: return@items
            TvPosterCard(
                imageUrl = info.subjectInfo.imageLarge,
                title = info.subjectInfo.displayName,
                onClick = { onClickSubject(info) },
                memoryId = "col-${info.subjectId}",
                modifier = Modifier.tvGridFocusItem(gridFocus, index, items.itemCount),
            )
        }
    }
}

/** 追番页默认值/调参. */
private object TvCollectionDefaults {
    /** 页面顶部留白 (tab 行上方). */
    val TopPadding = 24.dp

    /** tab 行左侧留白 (= overscan 安全边距 48). */
    val TabRowStartPadding = 48.dp

}

private fun UnifiedCollectionType.displayText(): String = when (this) {
    UnifiedCollectionType.WISH -> "想看"
    UnifiedCollectionType.DOING -> "在看"
    UnifiedCollectionType.ON_HOLD -> "搁置"
    UnifiedCollectionType.DONE -> "看过"
    UnifiedCollectionType.DROPPED -> "抛弃"
    UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
}
