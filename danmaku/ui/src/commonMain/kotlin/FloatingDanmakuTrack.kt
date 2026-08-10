/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
    baseSpeedPxPerSecond: Float,
    var safeSeparation: Float,
    var baseSpeedTextWidth: Int,
    val speedMultiplier: FloatState,
    private val randomizeSpeedFluctuation: Float = 0.0875f,
) : DanmakuTrack<T, FloatingDanmaku<T>> {
    private val danmakuList: MutableList<FloatingDanmaku<T>> = mutableListOf()

    /**
     * 基础速度. 修改时会以当前帧时间重新锚定轨道内所有弹幕, 保证位置连续 (不跳变), 弹幕立即以新速度运动.
     */
    var baseSpeedPxPerSecond: Float = baseSpeedPxPerSecond
        set(value) {
            if (field == value) return
            field = value
            danmakuList.forEach { it.updateSpeed(value * it.speedMultiplier) }
        }

    /**
     * 检测是否可以放置这条[弹幕][danmaku].
     *
     * 无论如何弹幕都不可以放到轨道长度之外.
     */
    override fun canPlace(danmaku: T, placeFrameTimeNanos: Long): Boolean {
        return checkPlaceableImpl(danmaku, placeFrameTimeNanos, overrideSpeedMultiplier = null) != null
    }

    override fun tryPlace(danmaku: T, placeFrameTimeNanos: Long): FloatingDanmaku<T>? {
        return tryPlace(danmaku, placeFrameTimeNanos, overrideSpeedMultiplier = null)
    }

    /**
     * 尝试放置弹幕.
     *
     * @param overrideSpeedMultiplier 指定速度倍率, 不重新计算随机速度波动.
     *     用于重新放置已在屏幕上的弹幕 (例如配置变更时), 保证弹幕位置与速度完全不变.
     */
    internal fun tryPlace(
        danmaku: T,
        placeFrameTimeNanos: Long,
        overrideSpeedMultiplier: Float?,
    ): FloatingDanmaku<T>? {
        val (upcomingDanmaku, insertionIndex) =
            checkPlaceableImpl(danmaku, placeFrameTimeNanos, overrideSpeedMultiplier) ?: return null
        if (insertionIndex < 0) danmakuList.add(upcomingDanmaku) else danmakuList.add(insertionIndex, upcomingDanmaku)

        return upcomingDanmaku
    }

    override fun place(danmaku: T, placeFrameTimeNanos: Long): FloatingDanmaku<T> {
        val upcomingDanmaku = danmaku.createFloating(placeFrameTimeNanos, overrideSpeedMultiplier = null)

        val insertionIndex = upcomingDanmaku.isNonOverlapping(danmakuList)
        if (insertionIndex < 0) danmakuList.add(upcomingDanmaku) else danmakuList.add(insertionIndex, upcomingDanmaku)

        return upcomingDanmaku
    }

    override fun clearAll() {
        danmakuList.clear()
    }

    override fun tick() {
        if (danmakuList.isEmpty()) return
        danmakuList.removeAll { danmaku -> danmaku.isGone() }
    }

    /**
     * 移除所有满足 [predicate] 的弹幕.
     */
    internal fun removeAll(predicate: (FloatingDanmaku<T>) -> Boolean) {
        danmakuList.removeAll(predicate)
    }

    /**
     * check if placeable, return insertionIndex and corresponding upcoming danmaku
     */
    private fun checkPlaceableImpl(
        danmaku: T,
        placeFrameTimeNanos: Long,
        overrideSpeedMultiplier: Float?,
    ): Pair<FloatingDanmaku<T>, Int>? {
        check(placeFrameTimeNanos == DanmakuTrack.NOT_PLACED || placeFrameTimeNanos >= 0) {
            "Expected placeFrameTimeNanos to be NOT_PLACED or non-negative, but got $placeFrameTimeNanos"
        }
        // 弹幕轨道宽度为 0 一定不能放
        if (trackWidth.intValue <= 0) return null
        // 无论如何都不能放置在轨道最右侧之外
        if (placeFrameTimeNanos != DanmakuTrack.NOT_PLACED && frameTimeNanosState.longValue - placeFrameTimeNanos < 0)
            return null

        // 如果指定了放置时间, 则需要计算划过的距离
        val upcomingDanmaku = danmaku.createFloating(placeFrameTimeNanos, overrideSpeedMultiplier)

        // 弹幕缓存为空, 那就判断是否 gone 了, 如果 gone 了就不放置
        if (danmakuList.isEmpty()) return if (upcomingDanmaku.isGone()) null else Pair(upcomingDanmaku, 0)
        // 如果弹幕右侧超过了轨道左侧, 则不放置
        if (upcomingDanmaku.isGone()) return null
        // 如果缓存不为空, 那就判断是否有重叠
        val insertionIndex = upcomingDanmaku.isNonOverlapping(danmakuList)
        return if (insertionIndex == -1) null else Pair(upcomingDanmaku, insertionIndex)
    }

    private fun T.createFloating(placeFrameTimeNanos: Long, overrideSpeedMultiplier: Float?): FloatingDanmaku<T> {
        require(danmakuWidth > 0) { "Expected danmaku width to be positive, but got $danmakuWidth." }
        val finalSpeedMultiplier = overrideSpeedMultiplier ?: run {
            val multiplier = this@FloatingDanmakuTrack.speedMultiplier.floatValue
                .pow(log(danmakuWidth.toFloat() / baseSpeedTextWidth, 2f))
                .coerceAtLeast(1f)

            if (randomizeSpeedFluctuation == 0f) multiplier
            else (multiplier + (Random.Default.nextFloat() - 0.5f) * 2f * randomizeSpeedFluctuation)
        }

        // 未指定放置时间的弹幕从当前帧时间开始滚动
        val anchorFrameTimeNanos = if (placeFrameTimeNanos == DanmakuTrack.NOT_PLACED) {
            frameTimeNanosState.longValue
        } else {
            placeFrameTimeNanos
        }

        return FloatingDanmaku(
            danmaku = this,
            placeFrameTimeNanos = anchorFrameTimeNanos,
            speedPxPerSecond = baseSpeedPxPerSecond * finalSpeedMultiplier,
            speedMultiplier = finalSpeedMultiplier,
            trackIndex = trackIndex,
            frameTimeNanosState = frameTimeNanosState,
            trackHeight = trackHeight,
        )
    }

    // 弹幕左侧在轨道的位置
    private fun FloatingDanmaku<T>.left() = trackWidth.intValue.toFloat() - distanceX

    // 弹幕右侧在轨道的位置
    private fun FloatingDanmaku<T>.right() = left() + danmaku.danmakuWidth + safeSeparation

    private fun FloatingDanmaku<T>.isGone(): Boolean {
        return right() <= 0
    }

    // 撞车检测, 必须让 previous.left 小于 next.left, 也就是 previous 在前 next 在后
    private fun willClash(previous: FloatingDanmaku<T>, next: FloatingDanmaku<T>): Boolean {
        // 前一条弹幕的右侧移动到轨道左侧(a.k.a isGone == true)花费的时间
        val previousRightReachTrackLeftCostTime = previous.right() / previous.speedPxPerSecond
        // 后一条弹幕的左侧移动到轨道左侧花费的时间
        val nextLeftReachTrackLeftCostTime = next.left() / next.speedPxPerSecond
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

    /**
     * 这个迭代器不是线程安全的, 访问迭代器时不保证 danmakuList.
     *
     * 因为这里需要在性能敏感的场景使用, 所以不创建 danmakuList 的拷贝.
     */
    override fun iterator(): Iterator<FloatingDanmaku<T>> {
        return object : Iterator<FloatingDanmaku<T>> {
            private var index = 0

            override fun hasNext(): Boolean {
                return index < danmakuList.size
            }

            override fun next(): FloatingDanmaku<T> {
                if (!hasNext()) throw NoSuchElementException()
                return danmakuList[index++]
            }
        }
    }

    override fun toString(): String {
        return "FloatingTrack(index=${trackIndex}, danmakuCount=${danmakuList.size})"
    }
}

/**
 * 一条浮动弹幕.
 *
 * 位置是[放置时间][placeFrameTimeNanos]与[速度][speedPxPerSecond]的纯函数,
 * 不做逐帧积分, 因此不会累计误差, 重新放置时也无需反推位置.
 */
@Stable
internal class FloatingDanmaku<T : SizeSpecifiedDanmaku>(
    var danmaku: T,
    placeFrameTimeNanos: Long,
    speedPxPerSecond: Float,
    // 这个值和字体大小无关
    internal val speedMultiplier: Float,
    internal val trackIndex: Int,
    private val frameTimeNanosState: LongState,
    private val trackHeight: IntState,
) {
    /**
     * 弹幕在帧时间轴上的锚点, 即弹幕位于轨道最右侧的时刻.
     */
    var placeFrameTimeNanos: Long = placeFrameTimeNanos
        private set

    /**
     * 弹幕的滚动速度. 单位 px/s. 等于轨道基础速度乘以 [speedMultiplier].
     */
    var speedPxPerSecond: Float = speedPxPerSecond
        private set

    /**
     * 弹幕在浮动轨道已滚动的距离, 是正数. 单位 px
     *
     * 例如, 如果弹幕现在在右侧刚被放置, 则等于 `0`.
     * 如果左边已滑倒轨道最左侧, 则等于轨道长度.
     */
    val distanceX: Float
        // 先用整数运算转换到微秒, 避免浮点数的量级过大
        get() = ((frameTimeNanosState.longValue - placeFrameTimeNanos) / 1_000L) / 1_000_000f * speedPxPerSecond

    /**
     * calculate pos y lazily in ui loop
     */
    var y = Float.NaN
        internal set

    internal fun calculatePosY(): Float {
        return trackHeight.intValue.toFloat() * trackIndex
    }

    /**
     * 修改速度, 并以当前帧时间重新锚定, 保证 [distanceX] 连续 (不跳变).
     */
    internal fun updateSpeed(newSpeedPxPerSecond: Float) {
        if (newSpeedPxPerSecond == speedPxPerSecond || newSpeedPxPerSecond <= 0f) return
        val currentDistanceX = distanceX
        placeFrameTimeNanos = frameTimeNanosState.longValue -
                (currentDistanceX / newSpeedPxPerSecond * 1_000_000_000f).toLong()
        speedPxPerSecond = newSpeedPxPerSecond
    }

    override fun toString(): String {
        return "FloatingDanmaku(elapsedX=$distanceX, y=$y)"
    }
}
