/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.compareTo
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

/**
 * 小屏幕上使用默认 TopAppBar 高度, 使用 36dp 头像; MEDIUM 及以上上增加额外 padding(all=8.dp), 并使用 48dp 头像
 *
 * Design: [NavigationSuiteScaffold on Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=15-605&t=gmFJS6LFQudIIXfK-4)
 *
 * 默认颜色为 [AniThemeDefaults.topAppBarColors]
 *
 * @param title use [AniTopAppBarDefaults.Title]
 *
 * @see TopAppBar
 */
@Composable
fun AniTopAppBar(
    title: @Composable () -> Unit,
    windowInsets: WindowInsets, // You would like to add only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    avatar: @Composable () -> Unit = {},
    searchIconButton: @Composable (() -> Unit)? = null,
    searchBar: @Composable (() -> Unit)? = null,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = AniThemeDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    WindowDragArea {
        val additionalPadding =
            if (windowSizeClass.windowWidthSizeClass.isAtLeastMedium && windowSizeClass.windowHeightSizeClass.isAtLeastMedium) {
                8.dp
            } else {
                0.dp
            }
        TopAppBar(
            title,
            modifier.padding(all = additionalPadding),
            navigationIcon,
            actions = {
                val horizontalPadding =
                    windowSizeClass.paneHorizontalPadding // refer to design on figma

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveSearchBar(
                        windowSizeClass,
                        searchIconButton,
                        Modifier.weight(1f, fill = false),
                        searchBar,
                    )
                    actions()
                }

                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .paddingIfNotEmpty(
                            start = horizontalPadding,
                            end = (horizontalPadding - 4.dp - additionalPadding).coerceAtLeast(0.dp), // `actions` 自带 4
                        ),
                ) {
                    Box(
                        Modifier.size(
                            if (windowSizeClass.windowWidthSizeClass.isAtLeastMedium
                                && windowSizeClass.windowHeightSizeClass.isAtLeastMedium
                            ) 48.dp else 36.dp,
                        ),
                    ) {
                        avatar()
                    }
                }
            },
            expandedHeight,
            windowInsets,
            colors,
            scrollBehavior,
        )
    }
}

@Stable
object AniTopAppBarDefaults {
    @Composable
    fun Title(text: String) {
        Text(text, Modifier.width(IntrinsicSize.Max), softWrap = false, maxLines = 1)
    }
}

@Composable
fun AdaptiveSearchBar(
    windowSizeClass: WindowSizeClass,
    searchIconButton: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    searchBar: @Composable (() -> Unit)?,
) {
    BoxWithConstraints(modifier) {
        AnimatedContent(
            calculateSearchBarSize(windowSizeClass.windowWidthSizeClass, maxWidth),
            Modifier.animateContentSize(),
            transitionSpec = { expandHorizontally(snap()) togetherWith shrinkHorizontally(snap()) },
            contentAlignment = Alignment.CenterEnd,
        ) { size ->
            when (size) {
                SearchBarSize.ICON_BUTTON -> if (searchIconButton != null) {
                    searchIconButton()
                }

                SearchBarSize.MEDIUM ->
                    if (searchBar != null) {
                        Box(
                            Modifier.sizeIn(minWidth = 240.dp, maxWidth = 360.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            searchBar()
                        }
                    }

                SearchBarSize.EXPANDED ->
                    if (searchBar != null) {
                        Box(
                            Modifier.sizeIn(minWidth = 360.dp, maxWidth = 480.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {

                            searchBar()
                        }
                    }
            }
        }
    }
}

private enum class SearchBarSize {
    ICON_BUTTON,
    MEDIUM,
    EXPANDED
}

private fun calculateSearchBarSize(
    windowWidthSizeClass: WindowWidthSizeClass,
    maxWidth: Dp,
): SearchBarSize {
    return when {
        windowWidthSizeClass >= WindowWidthSizeClass.EXPANDED && maxWidth >= 360.dp
            -> SearchBarSize.EXPANDED

        windowWidthSizeClass >= WindowWidthSizeClass.MEDIUM && maxWidth >= 240.dp -> SearchBarSize.MEDIUM
        else -> SearchBarSize.ICON_BUTTON
    }
}
