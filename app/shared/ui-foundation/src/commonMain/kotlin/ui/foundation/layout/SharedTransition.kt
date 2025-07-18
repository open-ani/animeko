/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

val LocalSharedTransitionScopeProvider: ProvidableCompositionLocal<SharedTransitionScopeProvider?> =
    compositionLocalOf { null }

/**
 * Provide [SharedTransitionScope] and [AnimatedVisibilityScope] to use shared transition modifiers
 * in deep-level composable component.
 */
interface SharedTransitionScopeProvider {
    val sharedTransitionScope: SharedTransitionScope
    val animatedVisibilityScope: AnimatedVisibilityScope
}

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