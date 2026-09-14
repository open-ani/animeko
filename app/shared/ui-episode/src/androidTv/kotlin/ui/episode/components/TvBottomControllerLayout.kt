/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The bottom layers slide/fade over a shared stationary scrim. */
@Composable
internal fun TvBottomControllerLayout(
    transition: Transition<Boolean>,
    recommendations: @Composable () -> Unit,
    controller: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Keep the shared scrim independent of both content transitions and panel height.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .4f to Color.Black.copy(alpha = .72f),
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
        )
        transition.AnimatedVisibility(
            visible = { !it },
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220, delayMillis = 160)) +
                    slideInVertically(tween(220, delayMillis = 160)) { -it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 10 },
        ) {
            controller()
        }
        transition.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220, delayMillis = 160)) +
                    slideInVertically(tween(220, delayMillis = 160)) { it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 10 },
        ) {
            recommendations()
        }
    }
}
