/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.utils.platform.annotations.TestOnly


/**
 * 通用的搜索状态.
 *
 * 数据实现: [PagingSearchState]
 *
 * UI: [SearchDefaults]
 *
 * All methods must be called on the main thread.
 */
@Stable
abstract class SearchState<T : Any> {
    /**
     * 当前搜索的 pager. 如果搜索未开始, 则此 flow 会 emit `null`.
     * 当清空搜索结果或重新开始搜索时, 此 flow 都会立即 emit `null` 以清空旧数据.
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
    return (pager ?: emptyFlow()).collectAsLazyPagingItemsWithLifecycle()
}

@Stable
val LazyPagingItems<*>.isLoadingFirstPage: Boolean
    get() = !loadState.isIdle && itemCount == 0

@Stable
val LazyPagingItems<*>.isLoadingFirstOrNextPage: Boolean
    get() = loadState.append is LoadState.Loading

@Stable
val LazyPagingItems<*>.hasFirstPage: Boolean
    get() = itemCount > 0

@Stable
val LazyPagingItems<*>.isFinishedAndEmpty: Boolean
    get() = itemCount == 0 && loadState.isIdle

@Immutable
sealed class SearchStage {
    data object Idle : SearchStage()
    data object Searching : SearchStage()
    data object Finished : SearchStage()
}

@Stable
class PagingSearchState<T : Any>(
    /**
     * 当 [startSearch] 时调用
     */
    private val createPager: () -> Flow<PagingData<T>>,
) : SearchState<T>() {
    private val currentPager: MutableStateFlow<Flow<PagingData<T>>?> = MutableStateFlow(null)
    override val pagerFlow: StateFlow<Flow<PagingData<T>>?> = currentPager.asStateFlow()

    override fun startSearch() {
        clear()
        currentPager.value = createPager()
    }

    override fun clear() {
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

@Stable
object SearchDefaults {
    @Composable
    fun <T : Any> ResultColumn(
        items: LazyPagingItems<T>,
        modifier: Modifier = Modifier,
        lazyListState: LazyListState = rememberLazyListState(),
        listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Unspecified),
        content: LazyListScope.() -> Unit,
    ) {
        LazyColumn(modifier.fillMaxWidth(), lazyListState) {
            stickyHeader {
                FastLinearProgressIndicator(
                    items.isLoadingFirstPage,
                    Modifier.padding(vertical = 4.dp),
                    minimumDurationMillis = 300,
                )
            }

            item { Spacer(Modifier.height(Dp.Hairline)) } // 如果空白内容, 它可能会有 bug

            item {
                ListItem(
                    headlineContent = {
                        when {
                            items.isFinishedAndEmpty -> {
                                Text("无搜索记录")
                            }

                            items.hasFirstPage -> {
                                Text("搜索到 ${items.itemCount} 个结果")
                            }
                        }
                    },
                    colors = listItemColors,
                )
            }

            content()

            item {
                if (items.isLoadingFirstOrNextPage && !items.isLoadingFirstPage) {
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
