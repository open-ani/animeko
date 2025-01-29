/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.animation.StandardAccelerate
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults.feedItemFadeOutSpec
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.app.ui.foundation.theme.NavigationMotionScheme

// 把过渡动画改为 fade 而不是带有回弹的 spring
/**
 * @see AnimatedPane
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
fun ThreePaneScaffoldPaneScope.ListDetailAnimatedPane(
    modifier: Modifier = Modifier,
    useSharedTransition: Boolean = false, // changed: for shared transitions
    content: (@Composable AnimatedVisibilityScope.() -> Unit),
) {
    val navMotionScheme by rememberUpdatedState(NavigationMotionScheme.current)
    val enterTransition by remember(useSharedTransition) {
        derivedStateOf {
            when {
                useSharedTransition -> {
                    fadeIn() + expandVertically()
                }

                paneRole == ListDetailPaneScaffoldRole.List -> {
                    navMotionScheme.popEnterTransition
                }

                paneRole == ListDetailPaneScaffoldRole.Detail -> {
                    navMotionScheme.enterTransition
                }

                else -> {
                    fadeIn(
                        tween(
                            EasingDurations.standardAccelerate,
                            delayMillis = EasingDurations.standardDecelerate,
                            easing = StandardAccelerate,
                        ),
                    )
                }
            }
        }
    }
    val exitTransition by remember(useSharedTransition) {
        derivedStateOf {
            when {
                useSharedTransition -> {
                    fadeOut() + shrinkVertically()
                }

                paneRole == ListDetailPaneScaffoldRole.List -> {
                    navMotionScheme.exitTransition
                }

                paneRole == ListDetailPaneScaffoldRole.Detail -> {
                    navMotionScheme.popExitTransition
                }

                else -> {
                    fadeOut(feedItemFadeOutSpec)
                }
            }
        }
    }
    return AnimatedPane(modifier, enterTransition, exitTransition, content = content)
}
