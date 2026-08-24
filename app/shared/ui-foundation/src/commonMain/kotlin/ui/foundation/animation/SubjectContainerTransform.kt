/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

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

/**
 * 条目卡片 <-> 条目详情页的 Material container transform 动效参数.
 *
 * 时长与页面导航动画 ([NavigationMotionScheme.TotalDurationMillis]) 对齐, 这样 predictive back 手势
 * 把整段动画 seek 到手势进度时, 容器形变也会跟着手指走.
 */
@Stable
object SubjectContainerTransform {
    /**
     * 新内容 (详情页) 在形变的容器里淡入, 与页面导航一样在 fade through 阈值之后才开始.
     */
    val ContentEnter: EnterTransition = fadeIn(
        tween(
            NavigationMotionScheme.TotalDurationMillis - PredictiveBackMotion.FadeOutDurationMillis,
            delayMillis = PredictiveBackMotion.FadeOutDurationMillis,
            easing = StandardDecelerateEasing,
        ),
    )

    /**
     * 旧内容 (卡片) 在形变的容器里淡出.
     */
    val ContentExit: ExitTransition = fadeOut(
        tween(PredictiveBackMotion.FadeOutDurationMillis, easing = StandardAccelerateEasing),
    )

    /**
     * 容器边界的形变动画. 用 tween 而不是默认的 spring, 才能被返回手势 seek.
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val Bounds: BoundsTransform = BoundsTransform { _, _ ->
        tween(NavigationMotionScheme.TotalDurationMillis, easing = EmphasizedEasing)
    }
}

@Immutable
private data class SubjectContainerTransformKey(val subjectId: Int)

/**
 * 把当前 layout 标记为条目 [subjectId] 的 container transform 容器.
 *
 * 列表里的条目卡片和条目详情页各打一次, 两边就会在导航时连成一个 Material container transform:
 * 卡片的边界形变成详情页的边界, 卡片内容淡出、详情页内容淡入. 只共享容器, 不共享封面图.
 *
 * 找不到配对 (卡片被滚出可组合范围、或者从搜索页之类没打标记的地方进入详情页) 时, 这个 modifier 不做
 * 任何事, 导航退回到 [NavigationMotionScheme.screen] 的普通页面动画.
 *
 * 注意按 `sharedBounds` 的要求, 裁剪要放在这个 modifier **之后**, 否则形变时的裁剪会丢失.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.subjectContainerTransform(subjectId: Int): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    // LocalNavAnimatedContentScope 没有默认值, 只有在 NavEntry 里才能读. LocalSharedTransitionScope
    // 只在 NavDisplay 外层提供, 所以能走到这里就一定在 NavEntry 内部.
    val animatedContentScope = LocalNavAnimatedContentScope.current
    val key = remember(subjectId) { SubjectContainerTransformKey(subjectId) }
    return with(sharedTransitionScope) {
        this@subjectContainerTransform.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animatedContentScope,
            enter = SubjectContainerTransform.ContentEnter,
            exit = SubjectContainerTransform.ContentExit,
            boundsTransform = SubjectContainerTransform.Bounds,
        )
    }
}
