/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

val LocalSharedTransitionScopeProvider: ProvidableCompositionLocal<SharedTransitionScopeProvider?> =
    staticCompositionLocalOf { null }

/**
 * Provide [SharedTransitionScope] and [AnimatedVisibilityScope] to use shared transition modifiers
 * in deep-level composable component.
 */
interface SharedTransitionScopeProvider {
    val sharedTransitionScope: SharedTransitionScope
    val animatedVisibilityScope: AnimatedVisibilityScope
}

@Composable
fun SharedTransitionScopeProvider(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): SharedTransitionScopeProvider {
    return object : SharedTransitionScopeProvider {
        override val sharedTransitionScope: SharedTransitionScope = sharedTransitionScope
        override val animatedVisibilityScope: AnimatedVisibilityScope = animatedVisibilityScope
    }
}

/**
 * supports only [CornerBasedShape]
 */
/*@Composable
fun AnimatedVisibilityScope.rememberAnimatedClippedBound(target: Shape): Shape {
    val density = LocalDensity.current
    if (target !is CornerBasedShape) return target
    
    val topStartTargetState = target.topEnd.toPx(Size.Zero, density)
    val topStart by transition.animateFloat { enterExit ->
        when (enterExit) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible, EnterExitState.PostExit -> topStartTargetState
        }
    }
    
    val topEndTargetState = target.topEnd.toPx(Size.Zero, density)
    val topEnd by transition.animateFloat { enterExit ->
        when (enterExit) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible, EnterExitState.PostExit -> topEndTargetState
        }
    }
    
    val bottomStartTargetState = target.topStart.toPx(Size.Zero, density)
    val bottomStart by transition.animateFloat { enterExit ->
        when (enterExit) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible, EnterExitState.PostExit -> bottomStartTargetState
        }
    }
    
    val bottomEndTargetState = target.topStart.toPx(Size.Zero, density)
    val bottomEnd by transition.animateFloat { enterExit ->
        when (enterExit) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible, EnterExitState.PostExit -> bottomEndTargetState
        }
    }
    
    return remember(topStart, topEnd, bottomStart, bottomEnd) {
        RoundedCornerShape(topStart, topEnd, bottomStart, bottomEnd)
    }
}*/

/**
 * Helper function to use [SharedTransitionScopeProvider] or not depending on
 * if [LocalSharedTransitionScopeProvider] provides a value.
 */
@Composable
fun Modifier.useSharedTransitionScope(
    block: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Modifier
) = composed {
    val sharedTransitionScopeProvider = LocalSharedTransitionScopeProvider.current ?: return@composed this
    sharedTransitionScopeProvider.sharedTransitionScope.block(
        this, sharedTransitionScopeProvider.animatedVisibilityScope,
    )
}