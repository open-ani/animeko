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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.him188.ani.app.navigation.SubjectDetailImageSharedElementKey
import me.him188.ani.app.ui.foundation.ifThen

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
 * 详情页那一侧封面图的 shared element key, 由详情页入口提供.
 *
 * 免得从入口一路把 key 传到封面那一层. 列表卡片那一侧不走这个 local, 直接把 key 传给
 * [Modifier.subjectImageSharedElement].
 */
@Stable
val LocalSubjectDetailImageSharedElementKey: ProvidableCompositionLocal<SubjectDetailImageSharedElementKey?> =
    staticCompositionLocalOf { null }

/**
 * 条目封面图的 shared element: 列表卡片的封面 <-> 详情页的封面.
 *
 * 嵌在 [subjectContainerTransform] 的容器里. 容器的 `alignment` 只管容器内容 (标题文字等) 在窗口里
 * 摆哪儿, 而封面图有自己的 bounds 动画, 画在 overlay 里, 所以是**精确对位**地从卡片飞到详情页封面,
 * 不受容器 alignment 影响.
 *
 * 几个源码层面的性质:
 * - 用 `sharedElement` 而不是 `sharedBounds`: 两侧是同一张图, 不需要 cross-fade.
 * - `renderInOverlayDuringTransition = true`, 所以容器的 `exit` 淡出**不会**作用在它身上 ——
 *   容器内容淡出、封面保持不透明地飞过去, 正是 Material container transform 的样子.
 * - `sharedElement` 内部 `renderOnlyWhenVisible = true`, 形变期间只画 target 那一侧
 *   (`SharedElementEntry.shouldRenderAtAll`), 不会出现两张图重叠.
 * - 形变期间子节点按 `Constraints.fixed(动画中的 bounds)` 重新测量
 *   (`SharedContentNode.approachMeasure`), 所以卡片的 9:16 和详情页的 849:1200 之间不会拉伸,
 *   只是裁剪范围在变.
 *
 * 不传 `boundsTransform`: 和 [subjectContainerTransform] 一样用默认的
 * `spring(stiffness = 400f, dampingRatio = 1f)`, 两条动画才不会脱节.
 *
 * 注意**不要**和 [Modifier.boundOffsetAlignment] 挂在同一个节点上: `sharedElement` 没有
 * `SkipToLookaheadSizeNode`, 挂上之后那个节点会按动画中的 bounds 测量, 锚点计算就会被污染.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.subjectImageSharedElement(
    key: SubjectDetailImageSharedElementKey? = LocalSubjectDetailImageSharedElementKey.current,
): Modifier {
    if (key == null) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedContentScope = LocalNavAnimatedContentScope.current
    val imageKey = remember(key) { key.copy(from = "${key.from}+image") }
    return with(sharedTransitionScope) {
        this@subjectImageSharedElement.sharedElement(
            rememberSharedContentState(imageKey),
            animatedVisibilityScope = animatedContentScope,
        )
    }
}

/**
 * @param alignmentState 形变从内容的哪一点向外扩. 传 `null` 时从页面顶部中间开始扩.
 * 详情页那一侧传 [rememberBoundOffsetAlignment] 建的 state, 让它跟着条目封面走;
 * 列表卡片那一侧不需要 —— 窗口涨大时卡片内容早就淡出了, 摆哪儿都看不出来.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.subjectContainerTransform(
    key: SubjectDetailImageSharedElementKey,
    alignmentState: BoundOffsetAlignmentState? = null,
    shape: Shape = MaterialTheme.shapes.large,
    enter: EnterTransition = LocalNavigationMotionScheme.current.predictiveSharedContainer.containerEnterTransition,
    exit: ExitTransition = LocalNavigationMotionScheme.current.predictiveSharedContainer.containerExitTransition,
): Modifier {
    val alignment: Alignment = alignmentState?.alignment ?: Alignment.TopCenter
    // 自定义 alignment 不会走 ScaleToBoundsCached 的缓存 (它只按 identity 认那 9 个标准 Alignment),
    // 而 ScaleToBoundsImpl 没有 equals, SkipToLookaheadSizeElement 是引用比较. 不 remember 的话每次
    // 重组都会判定为变化并重新测量.
    val resizeMode = remember(alignment) {
        SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.None, alignment)
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedContentScope = LocalNavAnimatedContentScope.current

    val bgKey = remember(key) { key.copy(from = "${key.from}+background") }

    return with(sharedTransitionScope) {
        val overlayClip = OverlayClip(shape)
        this@subjectContainerTransform
            .sharedBounds(
                rememberSharedContentState(bgKey),
                animatedVisibilityScope = animatedContentScope,
                clipInOverlayDuringTransition = overlayClip,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .sharedBounds(
                rememberSharedContentState(key),
                animatedVisibilityScope = animatedContentScope,
                resizeMode = resizeMode,
                enter = enter,
                exit = exit,
            )
            // 必须挂在内层 sharedBounds 之后: 这样才落在它的 SkipToLookaheadSizeNode 子树内,
            // 量到的是最终尺寸.
            .ifThen(alignmentState != null) {
                onPlaced { checkNotNull(alignmentState).onContainerPlaced(it) }
            }
    }
}
