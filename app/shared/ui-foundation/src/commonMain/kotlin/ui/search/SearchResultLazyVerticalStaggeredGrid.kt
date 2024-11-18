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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.domain.session.launchAuthorize
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.icons.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import org.koin.core.component.KoinComponent

/**
 * 显示搜索结果的 [LazyVerticalStaggeredGrid]. 支持显示加载中的进度条, 错误时显示错误卡片.
 *
 * @param problem 当有错误时调用. 内容可以是 [SearchProblemCard].
 */
@Composable
fun <T : Any> SearchResultLazyVerticalStaggeredGrid(
    items: LazyPagingItems<T>,
    problem: @Composable (problem: SearchProblem?) -> Unit,
    modifier: Modifier = Modifier,
    cells: StaggeredGridCells.Adaptive = StaggeredGridCells.Adaptive(300.dp),
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Unspecified),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
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
                    val value = items.rememberSearchProblemState().value
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

        FastLinearProgressIndicator(
            items.isLoadingFirstPage || items.loadState.refresh is LoadState.Loading,
            Modifier.zIndex(2f).align(Alignment.TopStart).fillMaxWidth().padding(vertical = 4.dp),
            minimumDurationMillis = 300,
        )
    }
}

/**
 * 一个卡片, 展示搜索时遇到的问题, 例如网络错误, 无搜索结果等.
 *
 * 提供按钮来解决错误, 例如 [onRetry].
 *
 * @param problem See [rememberSearchProblemState]
 * @param onRetry 当用户点击重试时调用. 只会在 [SearchProblem.NetworkError], [SearchProblem.ServiceUnavailable], [SearchProblem.UnknownError] 时调用.
 * @param onLogin 当用户点击登录时调用. 只会在 [SearchProblem.RequiresLogin] 时调用. 如果你的功能不需要登录, 可以传递一个空函数给此参数.
 *
 * @see SearchProblemCardLayout
 */ // https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=239-2230&node-type=section&t=moZBMAKgeQpptXRI-0
@Composable
fun SearchProblemCard(
    problem: SearchProblem?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = run {
        val navigator = LocalNavigator.current
        {
            object : KoinComponent {}.launchAuthorize(navigator)
        }
    },
    shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
    containerColor: Color = SearchDefaults.searchProblemContainerColor,
) {
    if (problem == null) return
    val role = SearchProblemCardRole.from(problem)

    val retryButton = @Composable {
        SearchDefaults.IconTextButton(
            onRetry,
            leadingIcon = { iconModifier ->
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
                        SearchDefaults.IconTextButton(
                            onLogin,
                            leadingIcon = { iconModifier ->
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
        }
    }


    SearchProblemCardLayout(
        role,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
    ) {
        content(cardColors)
    }
}

/**
 * 一个卡片, 展示搜索时遇到的问题, 例如网络错误, 无搜索结果等.
 *
 * @param role See [rememberSearchProblemState] and [SearchProblemCardRole.from]
 * @param content 可以是一个 [ListItem]. 使用 [SearchProblemCardScope.listItemColors] 来获取颜色.
 */
@Composable
fun SearchProblemCardLayout(
    role: SearchProblemCardRole,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
    containerColor: Color = SearchDefaults.searchProblemContainerColor,
    content: @Composable SearchProblemCardScope.() -> Unit,
) {
    role.Container(modifier, containerColor, shape, content)
}

@Stable
object SearchDefaults {
    val searchProblemContainerColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

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

@Stable
interface SearchProblemCardScope {
    val cardColors: CardColors
        @Composable get

    val listItemColors: ListItemColors
        @Composable get() = cardColors.run {
            ListItemDefaults.colors(
                containerColor = containerColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
                headlineColor = contentColor,
            )
        }
}
