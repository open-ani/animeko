/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun WithContentEnterAnimation(
    modifier: Modifier,
    enter: EnterTransition = LocalAniMotionScheme.current.animatedVisibility.screenEnter,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    var isContentReady by rememberSaveable {
        mutableStateOf(false)
    }
    SideEffect {
        isContentReady = true
    }

    AniAnimatedVisibility(
        isContentReady,
        modifier,
        // 从中间往上滑
        enter = enter,
        content = content,
    )
}