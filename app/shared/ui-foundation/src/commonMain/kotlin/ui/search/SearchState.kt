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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.launchAsLazyPagingItemsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 通用的搜索状态.
 *
 * 数据实现: [PagingSearchState]
 *
 * UI: [SearchResultLazyVerticalGrid]
 *
 * All methods must be called on the main thread.
 */
@Stable
abstract class SearchState<T : Any> {
    /**
     * 当前搜索的 pager. 如果搜索未开始, 则此 flow 会 emit `null`.
     * 当清空搜索结果或重新开始搜索时, 此 flow 都会立即 emit `null` 以清空旧数据.
     *
     * @see collectItemsWithLifecycle
     * @see collectHasQueryAsState
     */
    abstract val pagerFlow: StateFlow<Flow<PagingData<T>>?>

    /**
     * 清空当前所有的结果并且重新开始搜索.
     */
    abstract fun startSearch()

    /**
     * 清空所有搜索结果.
     */
    abstract fun clear()
}


/**
 * 收集当前搜索的物品.
 */
@Composable
fun <T : Any> SearchState<T>.collectItemsWithLifecycle(): LazyPagingItems<T> {
    val pagerFlow = pagerFlow
    val pager by pagerFlow.collectAsStateWithLifecycle(
        initialValue = pagerFlow.value,
    )
    @Suppress("UNCHECKED_CAST")
    return (pager ?: emptyPager as Flow<PagingData<T>>).collectAsLazyPagingItemsWithLifecycle()
}

/**
 * 收集当前搜索的物品.
 */
fun <T : Any> SearchState<T>.launchAsItemsIn(
    scope: CoroutineScope,
): LazyPagingItems<T> = pagerFlow.flatMapLatest { pager ->
    @Suppress("UNCHECKED_CAST")
    pager ?: emptyPager as Flow<PagingData<T>>
}.launchAsLazyPagingItemsIn(scope)

/**
 * 当搜索请求不为空时为 `true`.
 */
@Composable
fun <T : Any> SearchState<T>.collectHasQueryAsState(): State<Boolean> {
    val value by pagerFlow.collectAsStateWithLifecycle(
        initialValue = pagerFlow.value,
    )

    return remember {
        derivedStateOf {
            value != null
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Stable
private val emptyPager: Flow<PagingData<Any>> = flowOf(
    PagingData.from(
        emptyList(),
        sourceLoadStates = LoadStates(
            LoadState.NotLoading(endOfPaginationReached = true),
            LoadState.NotLoading(endOfPaginationReached = true),
            LoadState.NotLoading(endOfPaginationReached = true),
        ),
    ),
).cachedIn(GlobalScope)


@Stable
class PagingSearchState<T : Any>(
    /**
     * 当 [startSearch] 时调用
     */
    private val createPager: (scope: CoroutineScope) -> Flow<PagingData<T>>,
    // TODO: 2025/3/27 backgroundScope is just to fix a memory leak with minimal effort, see #1830. 
    //  We should refactor searching in the future.
    private val backgroundScope: CoroutineScope,
) : SearchState<T>() {
    private data class State<T : Any>(
        val scope: CoroutineScope,
        val pager: Flow<PagingData<T>>,
    )

    private val currentPager: MutableStateFlow<PagingSearchState.State<T>?> = MutableStateFlow(null)
    override val pagerFlow: StateFlow<Flow<PagingData<T>>?> = currentPager.map { it?.pager }
        .stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    override fun startSearch() {
        clear()

        val scope = backgroundScope.childScope()
        currentPager.value = State(
            scope,
            createPager(scope),
        )
    }

    override fun clear() {
        val prev = currentPager.value
        prev?.scope?.cancel()
        currentPager.value = null
    }
}

@TestOnly
class TestSearchState<T : Any>(
    override val pagerFlow: MutableStateFlow<Flow<PagingData<T>>?>,
) : SearchState<T>() {
    override fun startSearch() {
    }

    override fun clear() {
    }
}
