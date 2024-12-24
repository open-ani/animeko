/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.thenNotNull
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.search.LoadError
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.isLoadingNextPage
import me.him188.ani.app.ui.search.rememberLoadErrorState
import me.him188.ani.utils.platform.isMobile

@Composable
fun CommentColumn(
    state: CommentState,
    modifier: Modifier = Modifier,
    hasDividerLine: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    connectedScrollState: ConnectedScrollState? = null,
    commentItem: @Composable LazyItemScope.(index: Int, item: UIComment) -> Unit
) {
    val items = state.list.collectAsLazyPagingItemsWithLifecycle()

    PullToRefreshBox(
        isRefreshing = items.isLoadingFirstPageOrRefreshing,
        onRefresh = { items.refresh() },
        modifier = modifier,
        enabled = LocalPlatform.current.isMobile(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CommentColumnScaffold(
            items,
            problem = {
                LoadErrorCard(
                    problem = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.fillMaxWidth(), // noop
                )
            },
            modifier = Modifier
                .thenNotNull(
                    connectedScrollState?.let {
                        Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
                            .nestedScrollWorkaround(listState, connectedScrollState)
                    },
                ),
            lazyListState = listState,
            contentPadding = contentPadding,
        ) {
            items(
                items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType(),
            ) { index ->
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
        /*SearchResultLazyVerticalStaggeredGrid(
            items,
            problem = {
                LoadErrorCard(
                    problem = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.fillMaxWidth(), // noop
                )
            },
            modifier = Modifier
                .thenNotNull(
                    connectedScrollState?.let {
                        Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
                            .nestedScrollWorkaround(listState, connectedScrollState)
                    },
                ),
            contentPadding = contentPadding,
            progressIndicator = null,
        ) {
            item("spacer header") { Spacer(Modifier.height(1.dp)) }

            items(
                items.itemCount,
                key = items.itemKey { it.id },
                contentType = items.itemContentType(),
            ) { index ->
                val item = items[index] ?: return@items
                commentItem(index, item)

                if (hasDividerLine && index != items.itemCount - 1) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = DividerDefaults.color.stronglyWeaken(),
                    )
                }
            }

            if (items.isLoadingNextPage) {
                item("dummy loader") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }*/
    }
}

@Composable
private fun <T : Any> CommentColumnScaffold(
    items: LazyPagingItems<T>,
    problem: @Composable (problem: LoadError?) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Unspecified),
    horizontalArrangement: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        Column(Modifier.zIndex(1f)) {
            if (items.loadState.hasError) {
                Box(
                    Modifier
                        .sizeIn(
                            minHeight = Dp.Hairline,// 保证最小大小, 否则 LazyColumn 滑动可能有 bug
                            minWidth = Dp.Hairline,
                        )
                        .padding(vertical = 8.dp),
                ) {
                    val value = items.rememberLoadErrorState().value
                    problem(value)
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth(),
                state = lazyListState,
                horizontalAlignment = horizontalArrangement,
                contentPadding = contentPadding,
            ) {
                // 用于保持刷新时在顶部
                item("hairline") { Spacer(Modifier.height(Dp.Hairline)) } // 如果空白内容, 它可能会有 bug

                content()

                if (items.isLoadingNextPage) {
                    item("dummy loader") {
                        ListItem(
                            headlineContent = {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            },
                            colors = listItemColors,
                        )
                    }
                }
            }
        }
    }
}