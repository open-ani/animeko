/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.subject.collection.COLLECTION_TABS_SORTED
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusExit
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 追番页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvCollectionFocus : TvFocusKey {
    /** 当前选中的分类 tab (进页初始焦点; 网格按上/按返回的回归目标). 锚点随选中迁移. */
    CurrentTab,

    /** 网格首卡 (tab 行按下键的显式落点). */
    FirstCard,

    /** 边缘横向切 tab 后, 新列表里"对应位置"的卡 (锚点随 pendingFocusIndex 动态挂载). */
    EdgeEntryCard,
}

/**
 * TV 追番页 (atv-architecture.md §7.4):
 * 顶部 TabRow (聚焦即选中 + 数量角标) + Adaptive 网格.
 *
 * 状态层复用手机 UserCollectionsViewModel/UserCollectionsState (D3): 每 tab 独立缓存的
 * LazyPagingItems 与网格滚动状态 (跨 tab 保留数据与位置)、登录变更自动刷新.
 * [UserCollectionsViewModel.navigator] 是手机装配的 lateinit, TV 侧不触碰.
 */
@Composable
fun TvCollectionScreen(
    onClickSubject: (SubjectCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel<UserCollectionsViewModel> { UserCollectionsViewModel() }
    val state = viewModel.state
    val counts = state.collectionCounts
    val selectedTabIndex = state.selectedTypeIndex
    // 每 tab 独立缓存 (状态持有式 LazyPagingItems, desktop 消费模式同款)
    val items = remember(selectedTabIndex) {
        state.getCollectionLazyPagingItems(selectedTabIndex)
    }.collectWithLifecycle()

    // 统一焦点框架: 进页初始焦点落当前选中 tab; tab 行按下键直达网格首卡
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvCollectionFocus.CurrentTab)

    // 焦点在网格内时按返回: 直接回当前分类 tab (再按一次返回才交给壳回探索页)
    var gridHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = gridHasFocus) {
        focus.request(TvCollectionFocus.CurrentTab)
    }

    // 边缘横向切 tab: 网格左/右缘继续按左/右 = 切到相邻分类, 焦点落新列表"对应位置"
    // (同一行的近缘卡). focusedCardIndex 由卡片聚焦上报; pendingFocusIndex 是切换后
    // 待聚焦的目标下标 (新列表数据就绪后经 EdgeEntryCard 锚点送达).
    var focusedCardIndex by remember { mutableStateOf(-1) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }

    TvCollectionPageLayout(
        focus = focus,
        tabRow = {
            TvCollectionTabRow(
                selectedTabIndex = selectedTabIndex,
                counts = counts,
                focus = focus,
                onTabFocused = { index ->
                    // 聚焦即选中 (PR 语义, §5.2). 边缘切 tab 在途时冻结: 旧网格聚焦卡
                    // 销毁瞬间焦点会跌落到首 tab, 不冻结会把刚选的相邻 tab 又抢走
                    if (pendingFocusIndex == null && selectedTabIndex != index) {
                        state.selectTypeIndex(index)
                    }
                },
            )
        },
        modifier = modifier,
    ) {
        if (items.itemCount == 0) {
            TvCollectionEmptyPlaceholder(
                pendingFocusIndex = pendingFocusIndex,
                onReturnFocusToTab = {
                    pendingFocusIndex = null
                    focus.request(TvCollectionFocus.CurrentTab)
                },
            )
        } else {
            val gridState = state.getGridState(selectedTabIndex) // 跨 tab 保留滚动位置
            TvCollectionGrid(
                items = items,
                gridState = gridState,
                selectedTabIndex = selectedTabIndex,
                focus = focus,
                pendingFocusIndex = pendingFocusIndex,
                onClickSubject = onClickSubject,
                onCardFocused = { index, isEdgeEntry ->
                    focusedCardIndex = index
                    if (isEdgeEntry) pendingFocusIndex = null
                },
                onEdgeEntryTimeout = { pendingFocusIndex = null },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    // 网格上缘按上: 直达当前分类 tab (空间搜索会落到几何最近的 tab)
                    .tvFocusExit(focus, FocusDirection.Up to TvCollectionFocus.CurrentTab)
                    // 左/右缘继续按左/右: 切相邻分类, 焦点进新列表同行近缘卡.
                    // 非边缘不消费 (行内空间导航); 首 tab 左缘也不消费 -> 交给侧边栏 (聚焦即展开)
                    .onPreviewKeyEvent { event ->
                        val direction = when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> return@onPreviewKeyEvent false
                        }
                        val layout = gridState.layoutInfo
                        val info = layout.visibleItemsInfo.firstOrNull { it.index == focusedCardIndex }
                            ?: return@onPreviewKeyEvent false
                        val columns = (layout.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
                        val atEdge = if (direction < 0) {
                            info.column == 0
                        } else {
                            info.column == columns - 1 || info.index == items.itemCount - 1
                        }
                        if (!atEdge) return@onPreviewKeyEvent false
                        val targetTab = selectedTabIndex + direction
                        if (targetTab !in COLLECTION_TABS_SORTED.indices) {
                            // 首 tab 左缘: 放行 -> 侧边栏; 末 tab 右缘: 消费掉原地不动
                            return@onPreviewKeyEvent direction > 0
                        }
                        if (event.type == KeyEventType.KeyDown && !event.isAutoRepeat) {
                            // 对应位置 = 同一行、进入方向的近缘列 (右进落行首列, 左进落行尾列)
                            pendingFocusIndex = info.row * columns + if (direction > 0) 0 else columns - 1
                            state.selectTypeIndex(targetTab)
                        }
                        true
                    },
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
                    // 跨过网格上缘空隙直达首卡 (空间搜索跨大间距不可靠)
                    .tvFocusLink(focus, down = TvCollectionFocus.FirstCard),
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

/** 空列表占位. 边缘切进来的相邻 tab 是空列表时: 别让焦点悬空, 稍候归还当前 tab. */
@Composable
private fun TvCollectionEmptyPlaceholder(
    pendingFocusIndex: Int?,
    onReturnFocusToTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // (数据只是没加载完时, 网格会先出现使本效应取消, 不会误触)
    LaunchedEffect(pendingFocusIndex) {
        if (pendingFocusIndex != null) {
            delay(TvCollectionDefaults.EmptyTabReturnDelayMillis)
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

/**
 * 收藏网格: Adaptive 海报卡网格. 边缘切 tab 的按键判定在调用方 [modifier] 上
 * (依赖页面持有的 focusedCardIndex 等状态); 本组件负责"对应位置"送焦的锚点挂载与兜底.
 */
@Composable
private fun TvCollectionGrid(
    items: LazyPagingItems<SubjectCollectionInfo>,
    gridState: LazyGridState,
    selectedTabIndex: Int,
    focus: TvFocusScope,
    pendingFocusIndex: Int?,
    onClickSubject: (SubjectCollectionInfo) -> Unit,
    onCardFocused: (index: Int, isEdgeEntry: Boolean) -> Unit,
    onEdgeEntryTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 切 tab 后把"对应位置"送焦点: 等新列表分页就绪 -> 目标行滚进视口 -> 框架轮询聚焦
    LaunchedEffect(pendingFocusIndex, selectedTabIndex) {
        val target = pendingFocusIndex ?: return@LaunchedEffect
        snapshotFlow { items.itemCount }.first { it > 0 }
        val clamped = target.coerceAtMost(items.itemCount - 1)
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == clamped }) {
            gridState.scrollToItem(clamped)
        }
        focus.request(TvCollectionFocus.EdgeEntryCard)
        // 兜底超时: 送达即被 onFocused 清掉 (本效应随之取消); 万一没送达也要
        // 清 pending, 否则"聚焦即选中"被永久冻结
        delay(TvCollectionDefaults.EdgeEntryTimeoutMillis)
        onEdgeEntryTimeout()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TvPageDefaults.PosterGridCellMinWidth),
        state = gridState,
        modifier = modifier,
        contentPadding = TvPageDefaults.PosterGridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
        verticalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
    ) {
        items(items.itemCount, key = { items.peek(it)?.subjectId ?: it }) { index ->
            val info = items[index] ?: return@items
            val edgeEntryIndex = pendingFocusIndex?.coerceAtMost(items.itemCount - 1)
            TvPosterCard(
                imageUrl = info.subjectInfo.imageLarge,
                title = info.subjectInfo.displayName,
                onClick = { onClickSubject(info) },
                onFocused = { onCardFocused(index, index == edgeEntryIndex) },
                memoryId = "col-${info.subjectId}",
                modifier = Modifier
                    .then(
                        if (index == 0) {
                            Modifier.tvFocusAnchor(focus, TvCollectionFocus.FirstCard)
                        } else Modifier,
                    )
                    .then(
                        if (index == edgeEntryIndex) {
                            Modifier.tvFocusAnchor(focus, TvCollectionFocus.EdgeEntryCard)
                        } else Modifier,
                    ),
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

    /** 边缘切进空列表后焦点归还当前 tab 的等待 (给分页数据一个出现窗口). */
    const val EmptyTabReturnDelayMillis = 800L

    /** 边缘切 tab 送焦"对应位置"的兜底超时 (没送达也要清 pending, 否则聚焦即选中被永久冻结). */
    const val EdgeEntryTimeoutMillis = 1500L
}

/** 本次 KeyDown 是否系统按住连发 (边缘切 tab 只认离散按键, 防按住飞掠多个分类). */
private val KeyEvent.isAutoRepeat: Boolean
    get() = (nativeKeyEvent as? android.view.KeyEvent)?.let { it.repeatCount > 0 } ?: false

private fun UnifiedCollectionType.displayText(): String = when (this) {
    UnifiedCollectionType.WISH -> "想看"
    UnifiedCollectionType.DOING -> "在看"
    UnifiedCollectionType.ON_HOLD -> "搁置"
    UnifiedCollectionType.DONE -> "看过"
    UnifiedCollectionType.DROPPED -> "抛弃"
    UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
}
