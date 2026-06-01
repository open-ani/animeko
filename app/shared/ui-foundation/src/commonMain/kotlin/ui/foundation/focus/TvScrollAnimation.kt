/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.focus

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import kotlin.math.abs

/**
 * TV 焦点驱动滚动的动画器: 替代 [LazyListState.animateScrollToItem], 补上它缺的两样东西 ——
 * **可感知的时长**与**连发时的速度连续性**.
 *
 * 为什么不用自带的: `animateScrollToItem` 没有动画参数, 对可见目标用默认刚度 spring,
 * 单格距离 ~100-150ms 就结束 —— 低于人眼能看清运动过程的时长 (~200ms), 观感是"闪现到位",
 * 反而像掉帧. 且连按方向键时每次都从零速重启, 快速导航是"顿-冲-顿-冲"而不是连续流动.
 *
 * 本动画器的做法 (对齐 Leanback `GridLinearSmoothScroller` 的手感):
 * - 单格 ~260ms 的低刚度 spring, 快速启动、减速停靠, 运动过程可见;
 * - 动画被新目标取消时把**当前速度**带进下一段 ([velocity]), 连发时列表匀速流动,
 *   松手后自然减速停靠 —— 这是与"单纯调慢"的本质区别, 后者在连发下会积压卡顿;
 * - spring 对更远的目标自动提速, 连发追赶不积压.
 *
 * 用法: 每个列表 (每个 [LazyListState]/[LazyGridState]) 配一个实例, 生命周期跟随驱动滚动的
 * 协程 (effect 内 `val` 或组件级 `remember` 皆可, 重建只是把继承速度归零). 与其他滚动共享
 * scroll mutex: 新动画自动取消旧的.
 *
 * 成本: 与原实现逐帧工作量相同 (布局位移+重绘), 只是同样的位移摊到更多帧; 动画结束即静止,
 * 不产生常驻负载.
 */
@Stable
class TvScrollAnimator {
    /** 上一段动画被取消那一刻的速度 (px/s), 作为下一段的初速; 自然停靠后归零. */
    private var velocity = 0f

    /**
     * 平滑滚动到 [index], 停靠在视口起点偏移 [scrollOffset] 处 (语义同
     * [LazyListState.animateScrollToItem], 支持负值).
     * 目标不在组合中 (远跳, 如恢复焦点) 时回退自带实现 —— 那类场景本就该快进.
     */
    suspend fun animateScrollToItem(state: LazyListState, index: Int, scrollOffset: Int = 0) {
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (target == null) {
            velocity = 0f
            state.animateScrollToItem(index, scrollOffset)
            return
        }
        animateBy(state, (target.offset - scrollOffset).toFloat())
    }

    /** [LazyGridState] 版, 语义同上; 取主轴 offset. */
    suspend fun animateScrollToItem(state: LazyGridState, index: Int, scrollOffset: Int = 0) {
        val info = state.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (target == null) {
            velocity = 0f
            state.animateScrollToItem(index, scrollOffset)
            return
        }
        val mainAxisOffset = if (info.orientation == Orientation.Vertical) target.offset.y else target.offset.x
        animateBy(state, (mainAxisOffset - scrollOffset).toFloat())
    }

    private suspend fun animateBy(state: ScrollableState, distance: Float) {
        if (abs(distance) < 0.5f) {
            velocity = 0f
            return
        }
        val initialVelocity = velocity
        state.scroll {
            var consumedTotal = 0f
            AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateTo(
                targetValue = distance,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = TV_SCROLL_STIFFNESS,
                    visibilityThreshold = 0.5f,
                ),
            ) {
                val delta = value - consumedTotal
                val consumed = scrollBy(delta)
                consumedTotal += consumed
                // 每帧记录: 协程被新目标取消时, 这就是带进下一段的初速
                this@TvScrollAnimator.velocity = this.velocity
                // 碰到列表边界 (位移被 clamp), 剩余距离滚不动, 提前收尾
                if (abs(delta - consumed) > 0.5f) cancelAnimation()
            }
        }
        // 自然跑完 (或撞边界) 即静止; 只有中途被取消才保留速度 (上面的赋值), 不会执行到这里
        velocity = 0f
    }
}

/**
 * 焦点滚动 spring 刚度: 决定"单格滚动多久". 260f ≈ 260ms 停靠 (质量 1, 临界阻尼下
 * 停靠时间 ≈ 4/√stiffness 秒). 调大更快更利落, 调小更慢更从容; Leanback 的参照区间是
 * 单格 200-250ms. 只调这里, 全部 TV 焦点滚动 (网格吸顶/探索页行/选集轮播) 统一手感.
 */
private const val TV_SCROLL_STIFFNESS = 260f
