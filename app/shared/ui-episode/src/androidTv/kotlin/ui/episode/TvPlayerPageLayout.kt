/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private object TvPlayerPageLayoutDefaults {
    const val SidebarFraction = .38f
    const val SidebarAnimationMillis = 250
    val SidebarMaxWidth = 400.dp
    val VideoInset = 24.dp
}

/**
 * Player chrome and geometry. Content slots own their data and interaction semantics.
 * The decoder surface retains its measured size while the sidebar transforms the video layer.
 */
@Composable
internal fun TvPlayerPageLayout(
    sidebarTransition: Transition<Boolean>,
    video: @Composable (Modifier) -> Unit,
    status: @Composable BoxScope.() -> Unit,
    controller: @Composable BoxScope.() -> Unit,
    title: @Composable BoxScope.() -> Unit,
    indicator: @Composable BoxScope.() -> Unit,
    resolver: @Composable () -> Unit,
    sidebar: @Composable () -> Unit,
    overlays: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by sidebarTransition.animateFloat(
        transitionSpec = { tween(TvPlayerPageLayoutDefaults.SidebarAnimationMillis) }, label = "player-width",
    ) { if (it) 1f else 0f }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val sidebarWidth = (maxWidth * TvPlayerPageLayoutDefaults.SidebarFraction).coerceAtMost(TvPlayerPageLayoutDefaults.SidebarMaxWidth)
        video(
            Modifier.fillMaxSize().graphicsLayer {
                val inset = TvPlayerPageLayoutDefaults.VideoInset.toPx()
                val scale = (size.width - (sidebarWidth.toPx() + inset * 2) * progress) / size.width
                scaleX = scale
                scaleY = scale
                translationX = inset * progress
                transformOrigin = TransformOrigin(0f, .5f)
            },
        )
        Box(Modifier.width(maxWidth - sidebarWidth * progress).fillMaxHeight().testTag("tv-player-main")) {
            status()
            controller()
            title()
            indicator()
        }
        resolver()
        sidebarTransition.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.align(Alignment.CenterEnd).width(sidebarWidth).fillMaxHeight(),
            enter = slideInHorizontally(tween(TvPlayerPageLayoutDefaults.SidebarAnimationMillis)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(TvPlayerPageLayoutDefaults.SidebarAnimationMillis)) { it } + fadeOut(tween(180)),
        ) { sidebar() }
        overlays()
    }
}
