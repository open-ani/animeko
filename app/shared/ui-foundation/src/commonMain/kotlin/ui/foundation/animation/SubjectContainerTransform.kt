/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.him188.ani.app.navigation.SubjectDetailImageSharedElementKey

/**
 * 当前 `NavDisplay` 外层的 [SharedTransitionScope], 由 [SharedTransitionLayout] 提供.
 *
 * 为 `null` 时 [subjectContainerTransform] 退化成空实现 —— preview、单元测试、TV 端等没有包在
 * [SharedTransitionLayout] 里的地方都是这种情况.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.subjectContainerTransform(
    key: SubjectDetailImageSharedElementKey,
    enter: EnterTransition = LocalNavigationMotionScheme.current.predictiveSharedContainer.containerEnterTransition,
    exit: ExitTransition = LocalNavigationMotionScheme.current.predictiveSharedContainer.containerExitTransition,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedContentScope = LocalNavAnimatedContentScope.current

    val bgKey = remember(key) { key.copy(from = "${key.from}+background") }

    return with(sharedTransitionScope) {
        val overlayClip = OverlayClip(RoundedCornerShape(12.dp))
        this@subjectContainerTransform
            .sharedBounds(
                rememberSharedContentState(bgKey),
                animatedVisibilityScope = animatedContentScope,
                clipInOverlayDuringTransition = overlayClip,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            )
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .sharedBounds(
                rememberSharedContentState(key),
                animatedVisibilityScope = animatedContentScope,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.None, Alignment.TopCenter),
                clipInOverlayDuringTransition = overlayClip,
                enter = enter,
                exit = exit,
            )
    }
}
