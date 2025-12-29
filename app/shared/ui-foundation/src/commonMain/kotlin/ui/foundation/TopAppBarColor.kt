/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Composable
fun rememberCurrentTopAppBarContainerColor(
    colors: TopAppBarColors = AniThemeDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = TopAppBarDefaults.pinnedScrollBehavior(),
): State<Color> {
    // Obtain the container color from the TopAppBarColors using the `overlapFraction`. This
    // ensures that the colors will adjust whether the app bar behavior is pinned or scrolled.
    // This may potentially animate or interpolate a transition between the container-color and
    // the container's scrolled-color according to the app bar's scroll state.
    val targetColor by remember(colors, scrollBehavior) {
        derivedStateOf {
            val overlappingFraction = scrollBehavior?.state?.overlappedFraction ?: 0f
            lerp(
                colors.containerColor,
                colors.scrolledContainerColor,
                FastOutLinearInEasing.transform((if (overlappingFraction > 0.01f) 1f else 0f)),
            )
        }
    }

    return animateColorAsState(
        targetColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
}