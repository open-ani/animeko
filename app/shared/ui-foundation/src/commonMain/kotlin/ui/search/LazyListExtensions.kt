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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.minimumHairlineSize

/**
 * 展示一个错误提示卡片, 仅在加载失败时显示. 建议放在 [androidx.compose.foundation.lazy.LazyColumn] 第一个 item.
 * @see LoadErrorCard
 */
fun <T : Any> LazyListScope.loadErrorItem(
    items: LazyPagingItems<T>
) {
    if (items.loadState.hasError) {
        item {
            Box(Modifier.fillMaxWidth().minimumHairlineSize(), contentAlignment = Alignment.TopCenter) {
                val problem by items.rememberLoadErrorState()
                LoadErrorCard(
                    problem,
                    onRetry = {
                        items.refresh()
                    },
                )
            }
        }
    }
}

fun <T : Any> LazyListScope.noMoreItemsItem(
    items: LazyPagingItems<T>
) {
    when {
        items.loadState.append.endOfPaginationReached -> {
            item {
                val motionScheme = LocalAniMotionScheme.current
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).minimumHairlineSize().animateItem(
                        fadeInSpec = motionScheme.feedItemFadeInSpec,
                        placementSpec = motionScheme.feedItemPlacementSpec,
                        fadeOutSpec = motionScheme.feedItemFadeOutSpec,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "没有更多了",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 展示一个加载指示器, 仅在第一页或下一页加载时显示.
 *
 * @param ignoreRefresh 如果为 true, 则在刷新时不显示加载指示器. 这通常用于和 [me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox] 一起使用. 刷新时使用 PTR 的指示器.
 */
fun <T : Any> LazyListScope.loadingIndicatorItem(
    items: LazyPagingItems<T>,
    ignoreRefresh: Boolean = false,
) {
    when {
        if (ignoreRefresh) items.isLoadingNextPage else (items.isLoadingFirstPageOrRefreshing || items.isLoadingFirstOrNextPage) -> {
            item {
                val motionScheme = LocalAniMotionScheme.current
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).animateItem(
                        fadeInSpec = motionScheme.feedItemFadeInSpec,
                        placementSpec = motionScheme.feedItemPlacementSpec,
                        fadeOutSpec = motionScheme.feedItemFadeOutSpec,
                    ),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

/**
 * 组合 [noMoreItemsItem] 和 [loadingIndicatorItem] 的快捷方法.
 *
 * @param ignoreRefresh 如果为 true, 则在刷新时不显示加载指示器. 这通常用于和 [me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox] 一起使用. 刷新时使用 PTR 的指示器.
 */
fun <T : Any> LazyListScope.pagingFooterStateItem(
    items: LazyPagingItems<T>,
    ignoreRefresh: Boolean = false,
) {
    noMoreItemsItem(items)
    loadingIndicatorItem(items, ignoreRefresh)
}
