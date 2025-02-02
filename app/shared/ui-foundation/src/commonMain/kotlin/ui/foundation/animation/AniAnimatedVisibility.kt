/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
fun AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalAniMotionScheme.current.animatedVisibility.standardEnter,
    exit: ExitTransition = LocalAniMotionScheme.current.animatedVisibility.standardExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun RowScope.AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalAniMotionScheme.current.animatedVisibility.rowEnter,
    exit: ExitTransition = LocalAniMotionScheme.current.animatedVisibility.rowExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun ColumnScope.AniAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalAniMotionScheme.current.animatedVisibility.columnEnter,
    exit: ExitTransition = LocalAniMotionScheme.current.animatedVisibility.columnExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}
