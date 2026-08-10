/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui.layout

import me.him188.ani.danmaku.ui.DanmakuPresentation

/**
 * 弹幕布局的输入参数.
 *
 * 布局以**视频时间轴**为唯一时间轴: 所有时刻均为视频时间 (毫秒), 速度为像素每视频秒.
 * [playbackSpeed] 是布局参数之一 —— 弹幕的墙钟速度不随倍速变化 (产品行为),
 * 因此倍速变化时以新的 [playbackSpeed] [重新编译][DanmakuLayoutCompiler.compile]即可.
 */
data class DanmakuLayoutParams(
    /**
     * 浮动弹幕轨道长度 (即弹幕显示区域宽度). 单位 px.
     */
    val trackWidthPx: Int,
    val floatingTrackCount: Int,
    val topTrackCount: Int,
    val bottomTrackCount: Int,
    /**
     * 浮动弹幕基础速度, 墙钟时间. 单位 px/s.
     */
    val baseSpeedPxPerSecond: Float,
    /**
     * 播放倍速. 视频时间轴上的弹幕速度为墙钟速度除以倍速.
     */
    val playbackSpeed: Float = 1f,
    /**
     * 同轨道弹幕之间的最小间隔. 单位 px.
     */
    val safeSeparationPx: Float,
    /**
     * 速度倍率基准文本宽度. 单位 px. 宽度为此值的弹幕速度为 1 倍基础速度.
     */
    val baseSpeedTextWidthPx: Int,
    /**
     * 弹幕宽度为 2 倍 [baseSpeedTextWidthPx] 时的速度倍率.
     */
    val speedMultiplier: Float = 1.14f,
    /**
     * 速度随机波动幅度 θ: 最终速度倍率在 [倍率 - θ, 倍率 + θ] 内,
     * 由弹幕 key 的哈希确定, 与编译次数无关.
     */
    val speedFluctuation: Float = 0.0875f,
    /**
     * 固定弹幕 (顶部/底部) 显示时长, 墙钟时间. 单位 ms.
     */
    val fixedDanmakuDurationMillis: Long = 5000,
) {
    init {
        require(trackWidthPx > 0) { "trackWidthPx must be positive, got $trackWidthPx" }
        require(floatingTrackCount >= 0 && topTrackCount >= 0 && bottomTrackCount >= 0) {
            "track counts must be non-negative"
        }
        require(baseSpeedPxPerSecond > 0f) { "baseSpeedPxPerSecond must be positive" }
        require(playbackSpeed > 0f) { "playbackSpeed must be positive" }
        require(baseSpeedTextWidthPx > 0) { "baseSpeedTextWidthPx must be positive" }
    }
}

/**
 * 一条已布局的浮动弹幕: 轨道、进入时刻与速度在编译期完全确定,
 * 任意视频时刻的位置是 [distanceXAt] 的纯函数.
 */
class PlacedFloatingDanmaku(
    val presentation: DanmakuPresentation,
    val trackIndex: Int,
    /**
     * 弹幕右边缘出现在轨道最右侧的视频时刻. 单位 ms.
     */
    val enterTimeMillis: Long,
    val widthPx: Int,
    /**
     * 视频时间轴上的速度. 单位 px 每视频秒.
     */
    val speedPxPerVideoSecond: Float,
    /**
     * 弹幕左边缘 (含安全间隔) 完全滚出轨道左侧的视频时刻. 单位 ms.
     */
    val exitTimeMillis: Long,
) {
    /**
     * 在视频时刻 [videoTimeMillis] 已滚动的距离. 单位 px.
     * 弹幕右边缘的屏幕 x 坐标为 `trackWidthPx - distanceXAt(t)`.
     */
    fun distanceXAt(videoTimeMillis: Long): Float {
        return (videoTimeMillis - enterTimeMillis) / 1000f * speedPxPerVideoSecond
    }

    override fun toString(): String =
        "PlacedFloatingDanmaku(track=$trackIndex, enter=$enterTimeMillis, exit=$exitTimeMillis, " +
                "v=$speedPxPerVideoSecond, width=$widthPx, id=${presentation.danmaku.id})"
}

/**
 * 一条已布局的固定弹幕 (顶部或底部).
 */
class PlacedFixedDanmaku(
    val presentation: DanmakuPresentation,
    val trackIndex: Int,
    /**
     * 开始显示的视频时刻. 单位 ms.
     */
    val enterTimeMillis: Long,
    /**
     * 消失的视频时刻. 单位 ms.
     */
    val endTimeMillis: Long,
) {
    override fun toString(): String =
        "PlacedFixedDanmaku(track=$trackIndex, enter=$enterTimeMillis, end=$endTimeMillis, " +
                "id=${presentation.danmaku.id})"
}

/**
 * 一次[编译][DanmakuLayoutCompiler.compile]的结果: 整集弹幕的确定性布局.
 *
 * 相同输入必然产生相同输出; seek 只是移动读取窗口, 不需要任何重新计算.
 */
class CompiledDanmakuLayout(
    val params: DanmakuLayoutParams,
    /**
     * 按 [PlacedFloatingDanmaku.enterTimeMillis] 升序.
     */
    val floating: List<PlacedFloatingDanmaku>,
    /**
     * 按 [PlacedFixedDanmaku.enterTimeMillis] 升序.
     */
    val top: List<PlacedFixedDanmaku>,
    /**
     * 按 [PlacedFixedDanmaku.enterTimeMillis] 升序.
     */
    val bottom: List<PlacedFixedDanmaku>,
    /**
     * 因所有轨道均无法容纳而被丢弃的弹幕数量.
     */
    val droppedCount: Int,
)
