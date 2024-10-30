/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

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
        AniNavigationSuiteDefaults.calculateLayoutType(currentWindowAdaptiveInfo()),
//    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(), // Ani modified
    navigationContainerColor: Color = AniThemeDefaults.navigationContainerColor,
    navigationContentColor: Color = contentColorFor(AniThemeDefaults.navigationContainerColor),
    content: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier, color = navigationContainerColor, contentColor = navigationContentColor) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                WindowDragArea { // Ani modified: add WindowDragArea
                    navigationSuite()
                }
            },
            layoutType = layoutType,
            content = {
                Box(
                    Modifier.consumeWindowInsets(
                        when (layoutType) {
                            NavigationSuiteType.NavigationBar ->
                                NavigationBarDefaults.windowInsets

                            NavigationSuiteType.NavigationRail ->
                                NavigationRailDefaults.windowInsets

                            NavigationSuiteType.NavigationDrawer ->
                                DrawerDefaults.windowInsets

                            else -> WindowInsets(0, 0, 0, 0)
                        },
                    ),
                ) {
                    content()
                }
            },
        )
    }
}

@Stable
object AniNavigationSuiteDefaults {
    fun calculateLayoutType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {
            // ani changed: use NavigationRail on landscape phones
            if (windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
                && windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
            ) {
                return NavigationSuiteType.NavigationRail
            }
            // below is original logic

            if (windowPosture.isTabletop ||
                windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
            ) {
                NavigationSuiteType.NavigationBar
            } else if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED ||
                windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
            ) {
                NavigationSuiteType.NavigationRail
            } else {
                NavigationSuiteType.NavigationBar
            }
        }
    }
}

