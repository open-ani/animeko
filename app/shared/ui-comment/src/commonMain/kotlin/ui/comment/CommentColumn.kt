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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.thenNotNull
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.SearchResultLazyVerticalGrid
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.isLoadingNextPage
import me.him188.ani.utils.platform.isMobile

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentColumn(
    items: LazyPagingItems<UIComment>,
    modifier: Modifier = Modifier,
    hasDividerLine: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    connectedScrollState: ConnectedScrollState? = null,
    state: LazyGridState = rememberLazyGridState(),
    maxColumnWidth: Dp = Dp.Unspecified,
    commentItem: @Composable LazyGridItemScope.(index: Int, item: UIComment) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = items.isLoadingFirstPageOrRefreshing,
        onRefresh = { items.refresh() },
        modifier = modifier,
        enabled = LocalPlatform.current.isMobile(),
        contentAlignment = Alignment.TopCenter,
    ) {
        SearchResultLazyVerticalGrid(
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
                            .nestedScrollWorkaround(state, connectedScrollState)
                    },
                ),
            contentPadding = contentPadding,
            cells = GridCells.Fixed(1),
            showLoadingIndicatorInFirstPage = false, // Use PTR instead
            state = state,
        ) {
            item("spacer header") { Spacer(Modifier.height(1.dp)) }

            items(
                items.itemCount,
                key = items.itemKey { "CommentColumn-" + it.id },
                contentType = items.itemContentType(),
            ) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxColumnWidth)
                            .fillMaxWidth(),
                    ) {
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