/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
 * 有 shared element / container transform 参与的页面, 页面级导航动画.
 *
 * **不要**在这些页面上再叠加 [PredictiveBackMotion] 的全屏 `0.9` 缩放: shared bounds 自己已经在做
 * 缩放和位移, 两层叠起来会让 shared element 的路径、尺寸和落点都有"双重缩放"的感觉. 指南在
 * shared element 场景下也是要求让 shared element 本身完成返回运动.
 *
 * 所以这里只留一层淡入淡出, 而且用的是 Compose shared transition 那一套参数体系 —— `fadeIn()` /
 * `fadeOut()` 默认就是 `spring(stiffness = Spring.StiffnessMediumLow)`, 和 `sharedBounds` 默认的
 * 内容淡入淡出、以及默认 `BoundsTransform` 的 `spring(stiffness = 400f, dampingRatio = 1f)` 同族,
 * 不会互相拖节奏.
 */
val SharedTransitionNavTransition: ContentTransform
    get() = fadeIn() togetherWith fadeOut()

@Immutable
private data class SubjectContainerTransformKey(val subjectId: Int)

/**
 * 把当前 layout 标记为条目 [subjectId] 的 container transform 容器.
 *
 * 列表里的条目卡片和条目详情页各打一次, 两边就会在导航时连成一个 Material container transform:
 * 卡片的边界形变成详情页的边界, 卡片内容淡出、详情页内容淡入. 只共享容器, 不共享封面图.
 *
 * 动效参数全部用 Compose `sharedBounds` 的默认值, 也就是 shared transition 自己那套体系:
 * - bounds: `spring(stiffness = 400f, dampingRatio = 1f)`, 没有固定时长
 * - 内容淡入淡出: 同样是 `spring(stiffness = 400f)`
 * - resize: `ScaleToBounds(ContentScale.FillWidth, Alignment.Center)`
 * - `renderInOverlayDuringTransition = true`, 所以不会被父页面的 fade / clip 影响
 *
 * 页面级动画要配合 [SharedTransitionNavTransition] 使用, 不能用 predictive back 的全屏缩放.
 *
 * 找不到配对 (卡片被滚出可组合范围、或者从搜索页之类没打标记的地方进入详情页) 时, 这个 modifier 不做
 * 任何事.
 *
 * 注意按 `sharedBounds` 的要求, 裁剪要放在这个 modifier **之后**, 否则形变时的裁剪会丢失.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.subjectContainerTransform(subjectId: Int): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    // LocalNavAnimatedContentScope 没有默认值, 只有在 NavEntry 里才能读. 
    // LocalSharedTransitionScope 只在 NavDisplay 外层提供, 所以能走到这里就一定在 NavEntry 内部.
    val animatedContentScope = LocalNavAnimatedContentScope.current
    val key = remember(subjectId) { SubjectContainerTransformKey(subjectId) }
    return with(sharedTransitionScope) {
        this@subjectContainerTransform.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animatedContentScope,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.Center),
        )
    }
}
