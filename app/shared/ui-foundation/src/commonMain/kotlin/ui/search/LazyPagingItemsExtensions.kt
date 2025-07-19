/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
val LazyPagingItems<*>.isLoadingFirstPage: Boolean
    get() = !loadState.isIdle && !loadState.hasError && itemCount == 0

@Stable
val LazyPagingItems<*>.isLoadingFirstPageOrRefreshing: Boolean
    get() = isLoadingFirstPage || loadState.refresh is LoadState.Loading

@Stable
val LazyPagingItems<*>.isLoadingFirstOrNextPage: Boolean
    get() = isLoadingFirstPage || isLoadingNextPage

@Stable
val LazyPagingItems<*>.isLoadingNextPage: Boolean
    get() = loadState.append is LoadState.Loading

@Stable
val LazyPagingItems<*>.hasFirstPage: Boolean
    get() = itemCount > 0

@Stable
val LazyPagingItems<*>.isFinishedAndEmpty: Boolean
    get() = itemCount == 0 && loadState.isIdle


@TestOnly
@Composable
fun <T : Any> rememberTestLazyPagingItems(list: List<T>): LazyPagingItems<T> {
    return createTestPager(list).collectAsLazyPagingItems()
}

@TestOnly
fun <T : Any> createTestPager(list: List<T>) = MutableStateFlow(PagingData.from(list))
