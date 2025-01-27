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

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
    val role = paneRole
    val keepShowing =
        scaffoldStateTransition.currentState[role] != PaneAdaptedValue.Hidden &&
                scaffoldStateTransition.targetState[role] != PaneAdaptedValue.Hidden
//    val animateFraction = { scaffoldStateTransitionFraction }
    val navMotionScheme = NavigationMotionScheme.current
    scaffoldStateTransition.AnimatedVisibility(
        visible = { value: ThreePaneScaffoldValue -> value[role] != PaneAdaptedValue.Hidden },
        modifier =
            modifier
//            .animateBounds(
//                animateFraction = animateFraction,
//                positionAnimationSpec = tween(500, easing = EmphasizedEasing), // changed: custom animation spec
//                sizeAnimationSpec = tween(500, easing = EmphasizedEasing), // changed: custom animation spec
//                lookaheadScope = this,
//                enabled = keepShowing,
//            )
                .then(if (keepShowing) Modifier else Modifier.clipToBounds()),
        enter = when {
            useSharedTransition -> {
                fadeIn() + expandVertically()
            }

            role == ListDetailPaneScaffoldRole.List -> {
                navMotionScheme.popEnterTransition
            }

            role == ListDetailPaneScaffoldRole.Detail -> {
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
        }, // changed 原生的动画会回弹, 与目前的整个 APP 设计风格相差太多了
        exit = when {
            useSharedTransition -> {
                fadeOut() + shrinkVertically()
            }

            role == ListDetailPaneScaffoldRole.List -> {
                navMotionScheme.exitTransition
            }

            role == ListDetailPaneScaffoldRole.Detail -> {
                navMotionScheme.popExitTransition
            }

            else -> {
                fadeOut(feedItemFadeOutSpec)
            }
        }, // changed
    ) {
        this.content()
    }
}
