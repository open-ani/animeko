/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 可帮你迅速获得 [SharedTransitionScope] 和 [AnimatedVisibilityScope] 来在不需要动画的场景中调用一些设计了 shared transition 动画的组件.
 */
@Composable
inline fun DummySharedTransitionLayout(
    modifier: Modifier = Modifier,
    crossinline content: @Composable DummySharedTransitionLayoutScope.() -> Unit,
) {
    SharedTransitionLayout(modifier) {
        AnimatedVisibility(true) {
            val scope = remember(this@SharedTransitionLayout, this@AnimatedVisibility) {
                object : DummySharedTransitionLayoutScope, SharedTransitionScope by this@SharedTransitionLayout {
                    override val animatedVisibilityScope: AnimatedVisibilityScope get() = this@AnimatedVisibility
                }
            }
            scope.content()
        }
    }
}

interface DummySharedTransitionLayoutScope : SharedTransitionScope {
    val animatedVisibilityScope: AnimatedVisibilityScope
}
