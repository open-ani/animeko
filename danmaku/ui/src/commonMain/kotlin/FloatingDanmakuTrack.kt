/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.FloatState
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import kotlin.math.log
import kotlin.math.pow
import kotlin.random.Random

/**
 * FloatingDanmakuTrack 中的弹幕在以下情况会移除:
 * - tick 中的逻辑帧检测
 * - 调用 [DanmakuTrack.clearAll]
 * 移除时必须调用 [onRemoveDanmaku] 避免内存泄露.
 * 
 * @param baseSpeedPxPerSecond 放到这个轨道的弹幕里. 
 *     长度大于此基础长度才会加速弹幕运动, 等于此长度的弹幕速度为 1 倍 [baseSpeedPxPerSecond].
 * @param speedMultiplier 弹幕长度为 2 倍 [baseSpeedTextWidth] 时的速度倍率. 
 *     [DanmakuTrackProperties.speedMultiplier] 默认值为 1.14 倍.
 * @param randomizeSpeedFluctuation 弹幕速度随机波动范围.
 *     设 [randomizeSpeedFluctuation] 为 θ, 对于根据 [baseSpeedPxPerSecond] 与 [speedMultiplier] 计算的速度 α,
 *     最终的弹幕速度为 [[α - θ, α + θ]] 之间的随机值.
 */
@Stable
internal class FloatingDanmakuTrack<T : SizeSpecifiedDanmaku>(
    val trackIndex: Int,
    private val frameTimeNanosState: LongState,
    private val trackHeight: IntState,
    private val trackWidth: IntState,
    var baseSpeedPxPerSecond: Float,
    var safeSeparation: Float,
    var baseSpeedTextWidth: Int,
    val speedMultiplier: FloatState,
    private val randomizeSpeedFluctuation: Float = 0.0875f,
    // 某个弹幕需要消失, 必须调用此函数避免内存泄漏.
    private val onRemoveDanmaku: (FloatingDanmaku<T>) -> Unit
) : DanmakuTrack<T, FloatingDanmaku<T>> {
    private val danmakuList: MutableList<FloatingDanmaku<T>> = mutableListOf()

    /**
     * 检测是否可以放置这条[弹幕][danmaku].
     * 
     * 无论如何弹幕都不可以放到轨道长度之外.
     */
    override fun canPlace(danmaku: T, placeFrameTimeNanos: Long): Boolean {
        return checkPlaceableImpl(danmaku, placeFrameTimeNanos) != null
    }

    override fun tryPlace(danmaku: T, placeFrameTimeNanos: Long): FloatingDanmaku<T>? {
        val (upcomingDanmaku, insertionIndex) = checkPlaceableImpl(danmaku, placeFrameTimeNanos) ?: return null
        if (insertionIndex < 0) danmakuList.add(upcomingDanmaku) else danmakuList.add(insertionIndex, upcomingDanmaku)
        
        return upcomingDanmaku
    }

    override fun place(danmaku: T, placeFrameTimeNanos: Long): FloatingDanmaku<T> {
        val upcomingDanmaku = danmaku.createFloating(placeFrameTimeNanos)
        
        val insertionIndex = upcomingDanmaku.isNonOverlapping(danmakuList)
        if (insertionIndex < 0) danmakuList.add(upcomingDanmaku) else danmakuList.add(insertionIndex, upcomingDanmaku)
        
        return upcomingDanmaku
    }

    override fun clearAll() {
        danmakuList.removeAll {
            onRemoveDanmaku(it)
            true
        }
    }

    override fun tick() {
        if (danmakuList.isEmpty()) return
        danmakuList.removeAll { danmaku ->
            danmaku.isGone().also { if (it) onRemoveDanmaku(danmaku) }
        }
    }

    /**
     * check if placeable, return insertionIndex and corresponding upcoming danmaku
     */
    private fun checkPlaceableImpl(danmaku: T, placeFrameTimeNanos: Long): Pair<FloatingDanmaku<T>, Int>? {
        check(placeFrameTimeNanos == DanmakuTrack.NOT_PLACED || placeFrameTimeNanos >= 0) {
            "Expected placeFrameTimeNanos to be NOT_PLACED or non-negative, but got $placeFrameTimeNanos"
        }
        // 弹幕轨道宽度为 0 一定不能放
        if (trackWidth.value <= 0) return null
        // 无论如何都不能放置在轨道最右侧之外
        if (placeFrameTimeNanos != DanmakuTrack.NOT_PLACED && frameTimeNanosState.value - placeFrameTimeNanos < 0)
            return null

        // 如果指定了放置时间, 则需要计算划过的距离
        val upcomingDanmaku = danmaku.createFloating(placeFrameTimeNanos)

        // 弹幕缓存为空, 那就判断是否 gone 了, 如果 gone 了就不放置
        if (danmakuList.isEmpty()) return if (upcomingDanmaku.isGone()) null else Pair(upcomingDanmaku, 0)
        // 如果弹幕右侧超过了轨道左侧, 则不放置
        if (upcomingDanmaku.isGone()) return null
        // 如果缓存不为空, 那就判断是否有重叠
        val insertionIndex = upcomingDanmaku.isNonOverlapping(danmakuList)
        return if (insertionIndex == -1) null else Pair(upcomingDanmaku, insertionIndex)
    }

    private fun T.createFloating(placeFrameTimeNanos: Long): FloatingDanmaku<T> {
        require(danmakuWidth > 0) { "Expected danmaku width to be positive, but got $danmakuWidth." }
        val speedMultiplier = this@FloatingDanmakuTrack.speedMultiplier.value
            .pow(log(danmakuWidth.toFloat() / baseSpeedTextWidth, 2f))
            .coerceAtLeast(1f)
        
        val finalSpeedMultiplier = if (randomizeSpeedFluctuation == 0f) speedMultiplier
            else (speedMultiplier + (Random.Default.nextFloat() - 0.5f) * 2f * randomizeSpeedFluctuation)
        
        // 避免浮点数的量级过大
        val upcomingDistanceX = if (placeFrameTimeNanos == DanmakuTrack.NOT_PLACED) 0f else
            ((frameTimeNanosState.value - placeFrameTimeNanos) / 1_000L) / 1_000_000f * (baseSpeedPxPerSecond * finalSpeedMultiplier)
        
        return FloatingDanmaku(this, upcomingDistanceX, trackIndex, trackHeight, finalSpeedMultiplier)
    }
    
    // 弹幕左侧在轨道的位置
    private fun FloatingDanmaku<T>.left() = trackWidth.value.toFloat() - distanceX
    // 弹幕右侧在轨道的位置
    private fun FloatingDanmaku<T>.right() = left() + danmaku.danmakuWidth + safeSeparation

    private fun FloatingDanmaku<T>.isGone(): Boolean {
        return right() <= 0
    }

    // 撞车检测, 必须让 previous.left 小于 next.left, 也就是 previous 在前 next 在后
    private fun willClash(previous: FloatingDanmaku<T>, next: FloatingDanmaku<T>): Boolean {
        // 前一条弹幕的右侧移动到轨道左侧(a.k.a isGone == true)花费的时间
        val previousRightReachTrackLeftCostTime = previous.right() / previous.speedMultiplier
        // 后一条弹幕的左侧移动到轨道左侧花费的时间
        val nextLeftReachTrackLeftCostTime = next.left() / next.speedMultiplier
        // 如果 前一条弹幕的右侧移动到轨道左侧花费的时间 比 后一条弹幕的左侧移动到轨道左侧花费的时间 大
        // 那说明当 后一条弹幕的左侧 移动到轨道左侧时, 前一条弹幕的右侧 还需要花更长时间移动到轨道左侧, 会撞车
        return previousRightReachTrackLeftCostTime > nextLeftReachTrackLeftCostTime
    }

    // private fun FloatingDanmaku<T>.isFullyVisible(): Boolean {
    //     return distanceX >= danmaku.danmakuWidth + safeSeparation
    // }

    /**
     * 检测此浮动弹幕是否与 [list] 中的弹幕是否有重叠. 
     * 此方法假定 [list] 按 [FloatingDanmaku.distanceX] 倒序排序. 若不是, 则返回结果不可预测.
     * 
     * 返回插入 [list] 对应位置的索引. 此索引满足以下条件:
     * * 如果使用 `list.add(index, this)` 将此弹幕插入到 [list] 对应位置后, 保持上述的排序规则.
     * * 在[弹幕轨道长度范围][trackWidth]内滚动时, 此弹幕不会与它前一个弹幕和后一个弹幕重叠.
     *
     * @return `-1` 如果有重叠, 否则返回插入 [list] 对应位置的索引.
     */
    private fun FloatingDanmaku<T>.isNonOverlapping(list: List<FloatingDanmaku<T>>): Int {
        if (list.isEmpty()) return 0
        
        // fast path: 检查弹幕左侧是否比列表最后一个还大
        if (left() >= list.last().right()) {
            return if (willClash(list.last(), this)) -1 else list.size
        }

        // 下面是 chatgpt 写的
        // Perform binary search to find the insertion point
        val insertionIndex = list.binarySearch { it.left().compareTo(left()) }
        val index = if (insertionIndex < 0) -insertionIndex - 1 else insertionIndex

        // Check for overlap with the range at the insertion point and the one before it
        if (index < list.size && right() >= list[index].left()) {
            return -1
        }
        if (index > 0 && left() <= list[index - 1].right()) {
            return -1
        }

        // 此 index 保证 this 插入到 list 后 list 仍然是排序的, 由 binarySearch 保证.
        return when {
            index >= list.size -> if (willClash(list.last(), this)) -1 else list.size
            index == 0 -> if (willClash(this, list[0])) -1 else 0
            else -> {
                if (willClash(list[index - 1], this) || willClash(this, list[index])) -1 else index
            }
        }
    }

    /**
     * 获取最后一条弹幕(最新发送的弹幕)
     */
    internal fun getLastDanmaku(): FloatingDanmaku<T>? {
        return danmakuList.lastOrNull()
    }

    override fun toString(): String {
        return "FloatingTrack(index=${trackIndex}, danmakuCount=${danmakuList.size})"
    }
}

/**
 * 一条浮动弹幕
 */
@Stable
internal class FloatingDanmaku<T : SizeSpecifiedDanmaku>(
    var danmaku: T,
    initialDistanceX: Float = 0f,
    internal val trackIndex: Int,
    private val trackHeight: IntState,
    // 这个值和字体大小无关
    internal val speedMultiplier: Float,
) {
    /**
     * 弹幕在浮动轨道已滚动的距离, 是正数. 单位 px
     *
     * 例如, 如果弹幕现在在右侧刚被放置, 则等于 `0`.
     * 如果左边已滑倒轨道最左侧, 则等于轨道长度.
     */
    var distanceX: Float = initialDistanceX
        internal set

    /**
     * calculate pos y lazily in ui loop
     */
    var y = Float.NaN
        internal set

    internal fun calculatePosY(): Float {
        return trackHeight.value.toFloat() * trackIndex
    }

    override fun toString(): String {
        return "FloatingDanmaku(elapsedX=$distanceX, y=$y)"
    }
}