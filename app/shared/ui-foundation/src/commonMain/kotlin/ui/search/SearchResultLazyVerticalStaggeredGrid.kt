/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator

/**
 * 显示搜索结果的 [LazyVerticalStaggeredGrid]. 支持显示加载中的进度条, 错误时显示错误卡片.
 *
 * @param error 当有错误时调用. 内容可以是 [LoadErrorCard].
 */
@Composable
fun <T : Any> SearchResultLazyVerticalStaggeredGrid(
    items: LazyPagingItems<T>,
    error: @Composable (error: LoadError?) -> Unit,
    modifier: Modifier = Modifier,
    cells: StaggeredGridCells.Adaptive = StaggeredGridCells.Adaptive(300.dp),
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Unspecified),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    progressIndicator: @Composable (() -> Unit)? = {
        FastLinearProgressIndicator(
            items.isLoadingFirstPage || items.loadState.refresh is LoadState.Loading,
            Modifier.zIndex(2f).fillMaxWidth().padding(vertical = 4.dp),
            minimumDurationMillis = 300,
        )
    },
    content: LazyStaggeredGridScope.() -> Unit,
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
                    error(value)
                }
            }

            LazyVerticalStaggeredGrid(
                cells,
                Modifier.fillMaxWidth(),
                lazyStaggeredGridState,
                horizontalArrangement = horizontalArrangement,
                contentPadding = contentPadding,
            ) {
                // 用于保持刷新时在顶部
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(Dp.Hairline)) } // 如果空白内容, 它可能会有 bug

                content()

                if (items.isLoadingNextPage) {
                    item(span = StaggeredGridItemSpan.FullLine) {
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

        if (progressIndicator != null) {
            Box(Modifier.align(Alignment.TopStart)) {
                progressIndicator()
            }
        }
    }
}


@Stable
object SearchDefaults {
    @Composable
    fun SearchSummaryItem(
        items: LazyPagingItems<*>,
        modifier: Modifier = Modifier,
        containerColor: Color = Color.Unspecified,
    ) {
        Box(modifier) {
            when {
                items.isFinishedAndEmpty -> {
                    ListItem(
                        headlineContent = { Text("无搜索结果") },
                        colors = ListItemDefaults.colors(containerColor = containerColor),
                    )
                }

                items.hasFirstPage -> {
                    ListItem(
                        headlineContent = { Text("搜索到 ${items.itemCount} 个结果") },
                        colors = ListItemDefaults.colors(containerColor = containerColor),
                    )
                }

                else -> {
                    Spacer(Modifier.height(Dp.Hairline)) // 如果空白内容, 它可能会有 bug
                }
            }
        }
    }

    @Composable
    fun IconTextButton(
        onClick: () -> Unit,
        leadingIcon: @Composable (Modifier) -> Unit,
        modifier: Modifier = Modifier,
        text: @Composable () -> Unit,
    ) {
        TextButton(
            onClick,
            modifier,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            leadingIcon(Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            text()
        }
    }
}
