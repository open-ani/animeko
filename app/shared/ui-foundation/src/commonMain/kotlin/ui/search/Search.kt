/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.launchAsLazyPagingItemsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.paging.exceptions
import me.him188.ani.app.ui.foundation.icons.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24
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
)

@Stable
val LazyPagingItems<*>.isLoadingFirstPage: Boolean
    get() = !loadState.isIdle && !loadState.hasError && itemCount == 0

@Stable
val LazyPagingItems<*>.isLoadingFirstOrNextPage: Boolean
    get() = loadState.append is LoadState.Loading

@Stable
val LazyPagingItems<*>.isLoadingNextPage: Boolean
    get() = isLoadingFirstOrNextPage

@Stable
val LazyPagingItems<*>.hasFirstPage: Boolean
    get() = itemCount > 0

@Stable
val LazyPagingItems<*>.isFinishedAndEmpty: Boolean
    get() = itemCount == 0 && loadState.isIdle


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
    /**
     * @param problem [SearchProblemCard]
     */
    @Composable
    fun <T : Any> ResultColumn(
        items: LazyPagingItems<T>,
        problem: @Composable (problem: SearchProblem?) -> Unit,
        modifier: Modifier = Modifier,
        cells: StaggeredGridCells.Adaptive = StaggeredGridCells.Adaptive(300.dp),
        lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
        listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Unspecified),
        horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
        content: LazyStaggeredGridScope.() -> Unit,
    ) {
        Column(modifier) {
            FastLinearProgressIndicator(
                items.isLoadingFirstPage,
                Modifier.padding(vertical = 4.dp),
                minimumDurationMillis = 300,
            )

            if (items.loadState.hasError) {
                Box(
                    Modifier
                        .sizeIn(
                            minHeight = Dp.Hairline,// 保证最小大小, 否则 LazyColumn 滑动可能有 bug
                            minWidth = Dp.Hairline,
                        )
                        .padding(bottom = 8.dp),
                ) {
                    val value = items.rememberSearchErrorState().value
                    problem(value)
                }
            }

            LazyVerticalStaggeredGrid(
                cells,
                Modifier.fillMaxWidth(),
                lazyStaggeredGridState,
                horizontalArrangement = horizontalArrangement,
            ) {
                // 用于保持刷新时在顶部
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(Dp.Hairline)) } // 如果空白内容, 它可能会有 bug

                content()

                item(span = StaggeredGridItemSpan.FullLine) {
                    if (items.isLoadingNextPage) {
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

    /**
     * 一个卡片, 展示搜索时遇到的问题, 例如网络错误, 无搜索结果等.
     *
     * 提供按钮来解决错误, 例如 [onRetry].
     *
     * @param problem See [rememberSearchErrorState]
     * @param onRetry 当用户点击重试时调用. 只会在 [SearchProblem.NetworkError], [SearchProblem.ServiceUnavailable], [SearchProblem.UnknownError] 时调用.
     * @param onLogin 当用户点击登录时调用. 只会在 [SearchProblem.RequiresLogin] 时调用. 如果你的功能不需要登录, 可以传递一个空函数给此参数.
     */ // https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=239-2230&node-type=section&t=moZBMAKgeQpptXRI-0
    @Composable
    fun SearchProblemCard(
        problem: SearchProblem?,
        onRetry: () -> Unit,
        onLogin: () -> Unit,
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (problem == null) return


        @Composable
        fun IconTextButton(
            onClick: () -> Unit,
            icon: @Composable (Modifier) -> Unit,
            text: @Composable () -> Unit
        ) {
            TextButton(
                onClick,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                icon(Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                text()
            }
        }

        val retryButton = @Composable {
            IconTextButton(
                onRetry,
                icon = { iconModifier ->
                    Icon(
                        Icons.Rounded.Refresh, null,
                        iconModifier,
                    )
                },
                text = { Text("重试") },
            )
        }

        val content = @Composable { cardColors: CardColors ->
            val listItemColors = ListItemDefaults.colors(
                containerColor = cardColors.containerColor,
                leadingIconColor = cardColors.contentColor,
                trailingIconColor = cardColors.contentColor,
                headlineColor = cardColors.contentColor,
            )

            when (problem) {
                SearchProblem.NetworkError -> {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.WifiOff, null) },
                        headlineContent = { Text("网络错误") },
                        trailingContent = retryButton,
                        colors = listItemColors,
                    )
                }

                SearchProblem.RateLimited -> {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                        headlineContent = { Text("操作过快，请重试") },
                        trailingContent = retryButton,
                        colors = listItemColors,
                    )
                }

                SearchProblem.ServiceUnavailable -> {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.CloudOff, null) },
                        headlineContent = { Text("服务暂不可用") },
                        trailingContent = retryButton,
                        colors = listItemColors,
                    )
                }

                SearchProblem.NoResults -> {
                    ListItem(
                        leadingContent = { Spacer(Modifier.size(24.dp)) }, // spacer
                        headlineContent = { Text("无搜索结果") },
                        colors = listItemColors,
                    )
                }

                SearchProblem.RequiresLogin -> {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24, null) },
                        headlineContent = { Text("此功能需要登录") },
                        trailingContent = {
                            IconTextButton(
                                onLogin,
                                icon = { iconModifier ->
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Login, null,
                                        iconModifier,
                                    )
                                },
                                text = { Text("登录") },
                            )
                        },
                        colors = listItemColors,
                    )
                }

                is SearchProblem.UnknownError -> {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                        headlineContent = { Text("未知错误") },
                        trailingContent = {
                            Row {
                                if (currentAniBuildConfig.isDebug) {
                                    TextButton({ problem.throwable?.printStackTrace() }) {
                                        Text("Dump Trace", fontStyle = FontStyle.Italic)
                                    }
                                }

                                retryButton()
                            }
                        },
                        colors = listItemColors,
                    )
                }

                null -> {}
            }
        }


        when (problem) {
            // Important error
            is SearchProblem.UnknownError,
            SearchProblem.ServiceUnavailable,
            SearchProblem.NetworkError -> {
                val colors = CardDefaults.elevatedCardColors(
                    containerColor = containerColor,
                    contentColor = MaterialTheme.colorScheme.error,
                )
                ElevatedCard(
                    modifier, shape = shape,
                    colors = colors,
                ) {
                    content(colors)
                }
            }

            // Suggestive message
            SearchProblem.RequiresLogin -> {
                val colors = CardDefaults.elevatedCardColors(
                    containerColor = containerColor,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                ElevatedCard(
                    modifier, shape = shape,
                    colors = colors,
                ) {
                    content(colors)
                }
            }

            // Neutral message
            SearchProblem.RateLimited -> {
                val colors = CardDefaults.elevatedCardColors(
                    containerColor = containerColor,
                )
                ElevatedCard(
                    modifier, shape = shape,
                    colors = colors,
                ) {
                    content(colors)
                }
            }

            // Unimportant message
            SearchProblem.NoResults -> {
                val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ElevatedCard(
                    modifier,
                    colors = colors, // no 'boxing'
                    elevation = CardDefaults.cardElevation(), // no elevation
                ) {
                    content(colors)
                }
            }
        }
    }

    @Composable
    fun SearchSummaryItem(items: LazyPagingItems<*>, modifier: Modifier = Modifier) {
        Box(modifier) {
            when {
                items.isFinishedAndEmpty -> {
                    ListItem(
                        headlineContent = { Text("无搜索结果") },
                        colors = ListItemDefaults.colors(containerColor = Color.Unspecified),
                    )
                }

                items.hasFirstPage -> {
                    ListItem(
                        headlineContent = { Text("搜索到 ${items.itemCount} 个结果") },
                        colors = ListItemDefaults.colors(containerColor = Color.Unspecified),
                    )
                }

                else -> {
                    Spacer(Modifier.height(Dp.Hairline)) // 如果空白内容, 它可能会有 bug
                }
            }
        }
    }
}

@Composable
private fun <T : Any> LazyPagingItems<T>.rememberSearchErrorState(): State<SearchProblem?> {
    return remember(this) {
        derivedStateOf {
            SearchProblem.fromCombinedLoadStates(loadState)
        }
    }
}

/**
 * 搜索时遇到的问题.
 */
sealed class SearchProblem {
    data object NoResults : SearchProblem()
    data object RequiresLogin : SearchProblem()
    data object NetworkError : SearchProblem()
    data object ServiceUnavailable : SearchProblem()
    data object RateLimited : SearchProblem()
    data class UnknownError(val throwable: Throwable?) : SearchProblem()

    companion object {
        fun fromCombinedLoadStates(states: CombinedLoadStates): SearchProblem? {
            if (!states.hasError) {
                return null
            }
            val exceptions = states.exceptions()
            for (e in exceptions) {
                when (e) {
                    is RepositoryAuthorizationException -> return RequiresLogin
                    is RepositoryNetworkException -> return NetworkError
                    is RepositoryServiceUnavailableException -> return ServiceUnavailable
                    is RepositoryRateLimitedException -> return RateLimited
                }
            }
            return UnknownError(exceptions.firstOrNull())
        }

        fun fromException(e: Throwable): SearchProblem {
            return when (e) {
                is RepositoryAuthorizationException -> RequiresLogin
                is RepositoryNetworkException -> NetworkError
                is RepositoryServiceUnavailableException -> ServiceUnavailable
                is RepositoryRateLimitedException -> RateLimited
                else -> UnknownError(e)
            }
        }
    }
}


@TestOnly
@Composable
fun <T : Any> rememberTestLazyPagingItems(list: List<T>): LazyPagingItems<T> {
    return createTestPager(list).collectAsLazyPagingItems()
}

@TestOnly
fun <T : Any> createTestPager(list: List<T>) = MutableStateFlow(PagingData.from(list))
