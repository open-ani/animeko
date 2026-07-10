/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightCompact
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.LocalAppChromeOverlayInsets
import me.him188.ani.app.ui.foundation.theme.isAppChromeFrostedGlassActive

/**
 * @param navigationSuite use [AniNavigationSuite]
 *
 * @see NavigationSuiteScaffoldLayout
 * @see NavigationSuiteScaffold
 */
@Composable
fun AniNavigationSuiteLayout(
    navigationSuite: @Composable () -> Unit, // Ani modified
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        AniNavigationSuiteDefaults.calculateLayoutType(currentWindowAdaptiveInfo1()),
//    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(), // Ani modified
    navigationContainerColor: Color = AniThemeDefaults.navigationContainerColor,
    navigationContentColor: Color = contentColorFor(AniThemeDefaults.navigationContainerColor),
    content: @Composable () -> Unit = {},
) {
    // 毛玻璃导航栏需要内容延伸到导航栏下方, 才有内容可以模糊.
    val overlayNavigationBar = isAppChromeFrostedGlassActive() &&
            layoutType == NavigationSuiteType.NavigationBar

    Surface(modifier = modifier, color = navigationContainerColor, contentColor = navigationContentColor) {
        val consumedInsets = when (layoutType) {
            NavigationSuiteType.NavigationBar ->
                AniWindowInsets.forNavigationBar().only(WindowInsetsSides.Bottom)

            NavigationSuiteType.NavigationRail ->
                AniWindowInsets.forNavigationRail().only(WindowInsetsSides.Start)

            NavigationSuiteType.NavigationDrawer ->
                AniWindowInsets.forNavigationDrawer().only(WindowInsetsSides.Start)

            else -> WindowInsets.Zero
        }

        if (overlayNavigationBar) {
            AniNavigationBarOverlayLayout(
                navigationSuite = {
                    WindowDragArea { // Ani modified: add WindowDragArea
                        navigationSuite()
                    }
                },
                content = {
                    Box(Modifier.consumeWindowInsets(consumedInsets)) {
                        content()
                    }
                },
            )
        } else {
            NavigationSuiteScaffoldLayout(
                navigationSuite = {
                    WindowDragArea { // Ani modified: add WindowDragArea
                        navigationSuite()
                    }
                },
                layoutType = layoutType,
                content = {
                    Box(Modifier.consumeWindowInsets(consumedInsets)) {
                        content()
                    }
                },
            )
        }
    }
}

/**
 * Places [content] at full size with [navigationSuite] overlaid at the bottom, so that content can
 * scroll behind the frosted-glass navigation bar.
 *
 * The measured height of the navigation bar is published via [LocalAppChromeOverlayInsets] so that
 * page content can pad its scrollable content accordingly.
 */
@Composable
private fun AniNavigationBarOverlayLayout(
    navigationSuite: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val overlayInsets = remember { MutableWindowInsets() }
    CompositionLocalProvider(LocalAppChromeOverlayInsets provides overlayInsets) {
        Layout(
            content = {
                Box(Modifier.layoutId("navigationSuite")) { navigationSuite() }
                Box(Modifier.layoutId("content")) { content() }
            },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            // Measure the navigation bar first and publish its height, so that content measured
            // below reads the up-to-date insets in the same pass.
            val navigationPlaceable = measurables.first { it.layoutId == "navigationSuite" }
                .measure(looseConstraints)
            overlayInsets.insets = WindowInsets(bottom = navigationPlaceable.height)

            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val contentPlaceable = measurables.first { it.layoutId == "content" }
                .measure(constraints)

            layout(layoutWidth, layoutHeight) {
                contentPlaceable.place(0, 0)
                navigationPlaceable.place(0, layoutHeight - navigationPlaceable.height)
            }
        }
    }
}

@Stable
object AniNavigationSuiteDefaults {
    fun calculateLayoutType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {
            // ani changed: use NavigationRail on landscape phones
            if (windowSizeClass.isHeightCompact
                && windowSizeClass.isWidthAtLeastMedium
            ) {
                return NavigationSuiteType.NavigationRail
            }
            // below is original logic

            if (windowPosture.isTabletop ||
                windowSizeClass.isHeightCompact
            ) {
                NavigationSuiteType.NavigationBar
            } else if (windowSizeClass.isWidthAtLeastExpanded ||
                windowSizeClass.isWidthAtLeastMedium
            ) {
                NavigationSuiteType.NavigationRail
            } else {
                NavigationSuiteType.NavigationBar
            }
        }
    }
}
