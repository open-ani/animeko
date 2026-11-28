/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.thenNotNull
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchResultLazyVerticalGrid
import me.him188.ani.app.ui.search.isFinishedAndEmpty
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.isLoadingNextPage

/**
 * 在 Paging 刷新完成时调用 [CommentState.clearStaleOverlays], 使乐观覆盖不会永久遮住服务端刷新后的数据.
 */
@Composable
fun CommentOverlayCleanupEffect(state: CommentState, items: LazyPagingItems<UIComment>) {
    key(state) {
        CommentOverlayCleanupEffect(items, state::clearStaleOverlays)
    }
}

/** Allows MVI screens to dispatch refresh completion without receiving a mutable [CommentState]. */
@Composable
fun CommentOverlayCleanupEffect(items: LazyPagingItems<UIComment>, onRefreshCompleted: () -> Unit) {
    val currentOnRefreshCompleted by rememberUpdatedState(onRefreshCompleted)
    LaunchedEffect(items) {
        snapshotFlow { items.loadState.refresh }
            .distinctUntilChanged()
            .collect { refresh ->
                if (refresh is LoadState.NotLoading) {
                    currentOnRefreshCompleted()
                }
            }
    }
}

/**
 * @param pullToRefreshEnabled 是否启用下拉刷新. 当此列表位于会优先消费向下滚动的容器中时
 * (例如 [me.him188.ani.app.ui.foundation.layout.NestedScrollableColumn] 的 header 未完全展开),
 * 应传 `false`, 否则下拉刷新会先于容器消费掉下拉手势.
 * @param header 列表最前面的一项 (例如 thread 页的根评论与分组标题). 有表头时列表为空只在表头下方显示 [emptyPlaceholder], 表头保留.
 * @param emptyPlaceholder 没有任何评论时显示的内容.
 * @param isRefreshing 下拉刷新指示器是否转动. 默认跟随 [items] 的首屏加载 / 刷新; 调用方有别的加载 (例如表头) 时可以合并进来.
 * @param onRefresh 下拉刷新时调用, 默认刷新 [items].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentColumn(
    items: LazyPagingItems<UIComment>,
    modifier: Modifier = Modifier,
    hasDividerLine: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    connectedScrollState: ConnectedScrollState? = null,
    state: LazyGridState = rememberLazyGridState(),
    pullToRefreshEnabled: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    emptyPlaceholder: @Composable () -> Unit = { CommentDefaults.EmptyPlaceholder() },
    isRefreshing: Boolean = items.isLoadingFirstPageOrRefreshing,
    onRefresh: () -> Unit = { items.refresh() },
    commentItem: @Composable LazyGridItemScope.(index: Int, item: UIComment) -> Unit
) {
    val emptyContentModifier = Modifier
        .thenNotNull(
            connectedScrollState?.let {
                Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
            },
        )
    val listContentModifier = Modifier
        .thenNotNull(
            connectedScrollState?.let {
                Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
                    .nestedScrollWorkaround(state, connectedScrollState)
            },
        )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        enabled = pullToRefreshEnabled,
        touchOnly = true,
        contentAlignment = Alignment.TopCenter,
    ) {
        if (header == null && items.isFinishedAndEmpty) {
            Box(
                modifier = emptyContentModifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                emptyPlaceholder()
            }
            return@PullToRefreshBox
        }

        SearchResultLazyVerticalGrid(
            items,
            error = {
                LoadErrorCard(
                    error = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.fillMaxWidth(), // noop
                )
            },
            modifier = listContentModifier,
            contentPadding = contentPadding,
            cells = GridCells.Fixed(1),
            showLoadingIndicatorInFirstPage = false, // Use PTR instead
            state = state,
        ) {
            if (header != null) {
                item("header", contentType = "header") { header() }
            } else {
                item("spacer header") { Spacer(Modifier.height(1.dp)) }
            }

            if (header != null && items.isFinishedAndEmpty) {
                item("empty placeholder", contentType = "empty") { emptyPlaceholder() }
            }

            items(
                items.itemCount,
                key = items.itemKey { "CommentColumn-" + it.stableId },
                contentType = items.itemContentType(),
            ) { index ->
                Column {
                    val item = items[index] ?: return@items
                    commentItem(index, item)

                    if (hasDividerLine && index != items.itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = DividerDefaults.color.stronglyWeaken(),
                        )
                    }
                }
            }

            if (items.isLoadingNextPage) {
                item("dummy loader") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}
