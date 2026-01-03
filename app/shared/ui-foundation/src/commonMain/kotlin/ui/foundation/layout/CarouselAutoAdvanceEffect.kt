/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * 自动滚动 Carousel 的效果.
 *
 * 当 [enabled] 为 `true` 并且用户不在滚动时, 每隔 [period] 自动滚动到下一页.
 * 当滚动到页尾时, 会自动回到第一页.
 */
@Composable
fun CarouselAutoAdvanceEffect(
    enabled: Boolean,
    carouselState: CarouselState,
    period: Duration = 3.seconds,
    animationSpec: FiniteAnimationSpec<Float> = LocalAniMotionScheme.current.carouselAutoAdvanceSpec,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(carouselState, lifecycle, animationSpec) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 仅在 RESUMED 时自动滚动. 放到后台时不滚动.
            snapshotFlow { !enabled || carouselState.isScrollInProgress }.collectLatest { skip ->
                // 当用户正在滚动或者鼠标悬停时, 不自动滚动
                if (skip) {
                    return@collectLatest
                }

                while (currentCoroutineContext().isActive) {
                    delay(period)
                    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        val targetPage =
                            (carouselState.pagerState.currentPage + 1) % (carouselState.pagerState.pageCount)

                        val pager = carouselState.pagerState
                        val layoutInfo = pager.layoutInfo
                        val visiblePagesInfo = layoutInfo.visiblePagesInfo
                        val lastItem = visiblePagesInfo.lastOrNull() ?: return@launch

                        // 最后一个元素可见
                        if (lastItem.index == pager.pageCount - 1) {
                            // 如果最后一个 page 已经显示了 75% 以上, 说明最后一个元素顶到头了, 并且 pager size 也是填满屏幕的
                            // 这种情况就不滚动了, 直接回到第一页
                            if (layoutInfo.viewportEndOffset - lastItem.offset >= layoutInfo.pageSize * 0.75f) {
                                carouselState.animateScrollToItem(0, animationSpec)
                            } else {
                                // 
                                val scrollOffset =
                                    layoutInfo.pageSize - (layoutInfo.viewportEndOffset - lastItem.offset)
                                carouselState.animateScrollBy(scrollOffset.toFloat(), animationSpec)
                            }
                        } else {
                            if (targetPage < 0 || targetPage >= carouselState.pagerState.pageCount) {
                                return@launch // prevent crash
                            }
                            carouselState.animateScrollToItem(targetPage, animationSpec)
                        }
                    }
                }
            }
        }
    }
}
