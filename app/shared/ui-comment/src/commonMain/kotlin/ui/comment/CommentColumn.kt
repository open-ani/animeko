/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.thenNotNull
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchResultLazyVerticalStaggeredGrid
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.isLoadingNextPage
import me.him188.ani.utils.platform.isMobile

@Composable
fun CommentColumn(
    state: CommentState,
    modifier: Modifier = Modifier,
    hasDividerLine: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    connectedScrollState: ConnectedScrollState? = null,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    commentItem: @Composable LazyStaggeredGridItemScope.(index: Int, item: UIComment) -> Unit
) {
    val items = state.list.collectAsLazyPagingItemsWithLifecycle()

    PullToRefreshBox(
        isRefreshing = items.isLoadingFirstPageOrRefreshing,
        onRefresh = { items.refresh() },
        modifier = modifier,
        enabled = LocalPlatform.current.isMobile(),
        contentAlignment = Alignment.TopCenter,
    ) {
        SearchResultLazyVerticalStaggeredGrid(
            items,
            error = {
                LoadErrorCard(
                    error = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.fillMaxWidth(), // noop
                )
            },
            modifier = Modifier
                .thenNotNull(
                    connectedScrollState?.let {
                        Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
                            .nestedScrollWorkaround(lazyStaggeredGridState, connectedScrollState)
                    },
                ),
            lazyStaggeredGridState = lazyStaggeredGridState,
            contentPadding = contentPadding,
            progressIndicator = null,
        ) {
            item("spacer header") { Spacer(Modifier.height(1.dp)) }

            items(
                items.itemCount,
                key = items.itemKey { it.id },
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
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}