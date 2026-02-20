/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isDesktop

/**
 * 符合 Ani 设计风格的 TopAppBar: [NavigationSuiteScaffold on Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=15-605&t=gmFJS6LFQudIIXfK-4).
 *
 * ### search bar 布局
 *
 * - COMPACT: [searchIconButton], 这个按钮应该是一个搜索图标, 用于点击后展开搜索栏或进入搜索模式等.
 * - MEDIUM: [searchBar], 外显的搜索栏, 通常可以采用 [AdaptiveSearchBar].
 *
 * ### 其他布局细节信息
 *
 * | 设备类型 | 高度 | 头像大小 |
 * |----|----|-----|
 * | COMPACT | 使用[默认][TopAppBarDefaults.TopAppBarExpandedHeight] TopAppBar [高度][expandedHeight] | 24dp |
 * | MEDIUM+ | 默认高度以及额外 padding(all=8.dp) | 36.dp|
 *
 * 默认颜色为 [AniThemeDefaults.topAppBarColors]
 *
 * @param title use [AniTopAppBarDefaults.Title]
 * @param avatar 头像. 应当为一个圆形的头像, 且使用 `Modifier.size(recommendedSize)`.
 *
 * @see TopAppBar
 */
@Composable
fun AniTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    avatar: @Composable (recommendedSize: DpSize) -> Unit = {},
    searchIconButton: @Composable (() -> Unit)? = null,
    searchBar: @Composable (() -> Unit)? = null,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = AniThemeDefaults.topAppBarColors(),
    windowInsets: WindowInsets = AniWindowInsets.forTopAppBar()
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), // You would like to add only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    scrollBehavior: TopAppBarScrollBehavior? = null,
    size: TopAppBarSize = TopAppBarSize.SMALL,
) {
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass

    val platformWindow = LocalPlatformWindow.current
    WindowDragArea(
        Modifier.ifThen(LocalPlatform.current.isDesktop()) {
            // 双击标题栏全屏
            combinedClickable(
                onDoubleClick = {
                    if (platformWindow.isExactlyMaximized) {
                        platformWindow.floating()
                    } else {
                        platformWindow.maximize()
                    }
                },
                interactionSource = null,
                indication = null,
            ) {}
        },
    ) {
        val additionalPadding =
            if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
                8.dp
            } else {
                0.dp
            }
        val actionsDecorated: @Composable RowScope.() -> Unit = {
            val horizontalPadding =
                windowSizeClass.paneHorizontalPadding // refer to design on figma

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveSearchBarLayout(
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
                    .padding(end = additionalPadding)
                    .paddingIfNotEmpty(
                        start = horizontalPadding,
                        end = (horizontalPadding - 4.dp - additionalPadding).coerceAtLeast(0.dp), // `actions` 自带 4
                    ),
            ) {
                val minSize =
                    if (windowSizeClass.isWidthAtLeastMedium
                        && windowSizeClass.isHeightAtLeastMedium
                    ) {
                        48.dp
                    } else {
                        36.dp
                    }
                Box(
                    Modifier.sizeIn(
                        minWidth = minSize, maxWidth = 128.dp,
                        minHeight = minSize, maxHeight = minSize,
                    ),
                ) {
                    avatar(DpSize(minSize, minSize))
                }
            }
        }

        when (size) {
            TopAppBarSize.SMALL -> {
                TopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = modifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    expandedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }

            TopAppBarSize.MEDIUM -> {
                MediumTopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = modifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    collapsedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }

            TopAppBarSize.LARGE -> {
                LargeTopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = modifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    collapsedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    }
}

@Immutable
enum class TopAppBarSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Stable
object AniTopAppBarDefaults {
    @Composable
    fun Title(text: String) {
        Text(text, Modifier.width(IntrinsicSize.Max), softWrap = false, maxLines = 1)
    }
}

/**
 * 自适应搜索栏布局.
 *
 * @param searchIconButton 搜索按钮, 通常为一个搜索图标.
 * @param searchBar 外显搜索栏. 可以是 [PopupSearchBar]
 */
@Composable
private fun AdaptiveSearchBarLayout(
    windowSizeClass: WindowSizeClass,
    searchIconButton: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    searchBar: @Composable (() -> Unit)?,
) {
    BoxWithConstraints(modifier) {
        AnimatedContent(
            calculateSearchBarSize(windowSizeClass, maxWidth),
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
    windowSizeClass: WindowSizeClass,
    maxWidth: Dp,
): SearchBarSize {
    return when {
        windowSizeClass.isWidthAtLeastExpanded && maxWidth >= 360.dp
            -> SearchBarSize.EXPANDED

        windowSizeClass.isWidthAtLeastMedium && maxWidth >= 240.dp -> SearchBarSize.MEDIUM
        else -> SearchBarSize.ICON_BUTTON
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
private fun PreviewAniTopAppBar() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    AniTopAppBar(
        title = { Text("MyTitle") },
        navigationIcon = { BackNavigationIconButton({}) },
        actions = {
            IconButton({}) {
                Icon(Icons.Rounded.Settings, null)
            }
        },
        avatar = { recommendedSize ->
            SelfAvatar(
                TestSelfInfoUiState,
                size = recommendedSize,
                onClick = { },
            )
        },
        searchIconButton = {
            IconButton({}) {
                Icon(Icons.Rounded.Search, null)
            }
        },
        searchBar = {
            IconButton({}) {
                Icon(Icons.Rounded.Search, null)
            }
        },
        windowInsets = WindowInsets.Zero,
    )
}
