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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
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

/**
 * 显示搜索结果的 [LazyVerticalStaggeredGrid]. 支持显示加载中的进度条, 错误时显示错误卡片.
 *
 * @param error 当有错误时调用. 内容可以是 [LoadErrorCard].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : Any> SearchResultLazyVerticalGrid(
    items: LazyPagingItems<T>,
    error: @Composable (error: LoadError?) -> Unit,
    modifier: Modifier = Modifier,
    cells: GridCells = GridCells.Adaptive(360.dp),
    state: LazyGridState = rememberLazyGridState(),
    listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showLoadingIndicatorInFirstPage: Boolean = true,
    content: LazyGridScope.() -> Unit,
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

            LazyVerticalGrid(
                cells,
                Modifier.fillMaxWidth(),
                state,
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = verticalArrangement,
                contentPadding = contentPadding,
            ) {
                content()

                // 在加载第一页时不显示, 避免有两个, #1835
                if (items.loadState.refresh is LoadState.Loading) {
                    if (showLoadingIndicatorInFirstPage || !items.isLoadingFirstPage) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ListItem(
                                headlineContent = {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        LoadingIndicator()
                                    }
                                },
                                colors = listItemColors,
                            )
                        }
                    }
                }
                
                // 显示下一页加载指示器，否则加载速度慢用户不知道是否在更新
                if (items.loadState.append is LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ListItem(
                            headlineContent = {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    LoadingIndicator()
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


@Stable
object SearchDefaults {

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
