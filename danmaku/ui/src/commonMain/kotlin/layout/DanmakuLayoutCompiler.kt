/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui.layout

import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.ui.DanmakuPresentation
import kotlin.math.log
import kotlin.math.pow

/**
 * 弹幕布局编译器: 把整集弹幕列表一次性编译为确定性的 [CompiledDanmakuLayout].
 *
 * ## 设计
 *
 * 与"弹幕事件到达时在线贪心放置"不同, 编译器把布局当作纯函数:
 *
 * ```
 * (弹幕列表, 布局参数, 宽度测量) -> 每条弹幕固定的 (轨道, 进入时刻, 速度)
 * ```
 *
 * - **确定性**: 随机速度波动由弹幕 key 的哈希决定, 同一集在任何设备、任何时刻编译结果一致;
 * - **seek 零成本**: 运行时只需按当前视频时刻取可见窗口, 快进/快退没有任何"重新装填"概念;
 * - **单一时间轴**: 一切以视频时间表达, 播放倍速是布局参数 ([DanmakuLayoutParams.playbackSpeed]),
 *   变化时重新编译 (毫秒级);
 * - **前缀冻结**: 列表增量到达 / 参数变化时, 传入上一次的编译结果和冻结时刻,
 *   已进入屏幕的弹幕保持原布局逐条复用, 只重排未来部分, 屏幕上永不跳变.
 *   新放置的弹幕会同时对已冻结的未来弹幕做避碰检查, 非重叠不变量在冻结下依然成立.
 *
 * ## 碰撞规则 (同轨道, prev 在前 next 在后)
 *
 * 1. next 出现时 prev 必须已完全进入轨道 (含安全间隔);
 * 2. next 的左边缘到达轨道左侧不早于 prev 完全滚出 (不追尾).
 *
 * 两条均与"当前时刻"无关, 因此对过去时刻的放置 (例如新启用弹幕源的历史弹幕) 同样成立.
 */
object DanmakuLayoutCompiler {
    /**
     * @param list 弹幕列表, 无需预先排序.
     * @param measureWidth 测量弹幕文本宽度 (px). 必须是纯函数.
     * @param previous 上一次编译结果, 用于前缀冻结.
     * @param freezeBeforeMillis [previous] 中进入时刻早于此视频时刻的弹幕保持原布局不变
     *   (仅当它仍在 [list] 中且轨道号仍然有效).
     */
    fun compile(
        list: List<DanmakuPresentation>,
        params: DanmakuLayoutParams,
        measureWidth: (DanmakuPresentation) -> Int,
        previous: CompiledDanmakuLayout? = null,
        freezeBeforeMillis: Long = Long.MIN_VALUE,
    ): CompiledDanmakuLayout {
        val sorted = list.sortedBy { it.danmaku.playTimeMillis }
        val newKeys = sorted.mapTo(HashSet(sorted.size)) { danmakuLayoutKey(it) }

        val floating = FloatingPass(params, measureWidth)
        val top = FixedPass(params, params.topTrackCount)
        val bottom = FixedPass(params, params.bottomTrackCount)

        if (previous != null) {
            floating.seedFrozen(previous.floating, freezeBeforeMillis, newKeys)
            top.seedFrozen(previous.top, freezeBeforeMillis, newKeys)
            bottom.seedFrozen(previous.bottom, freezeBeforeMillis, newKeys)
        }

        var dropped = 0
        for (presentation in sorted) {
            val placedOk = when (presentation.danmaku.location) {
                DanmakuLocation.NORMAL -> floating.place(presentation)
                DanmakuLocation.TOP -> top.place(presentation)
                DanmakuLocation.BOTTOM -> bottom.place(presentation)
            }
            if (!placedOk) dropped++
        }

        return CompiledDanmakuLayout(
            params = params,
            floating = floating.result,
            top = top.result,
            bottom = bottom.result,
            droppedCount = dropped,
        )
    }

    private class FloatingPass(
        private val params: DanmakuLayoutParams,
        private val measureWidth: (DanmakuPresentation) -> Int,
    ) {
        val result = ArrayList<PlacedFloatingDanmaku>()

        /** 每轨道最后一条已放置的弹幕 */
        private val lastPlaced = arrayOfNulls<PlacedFloatingDanmaku>(params.floatingTrackCount)

        /** 每轨道尚未复用的冻结弹幕, 按进入时刻升序 */
        private val frozenQueues = Array(params.floatingTrackCount) { ArrayDeque<PlacedFloatingDanmaku>() }

        private var frozenByKey: Map<String, PlacedFloatingDanmaku> = emptyMap()

        fun seedFrozen(
            previousPlaced: List<PlacedFloatingDanmaku>,
            freezeBeforeMillis: Long,
            newKeys: Set<String>,
        ) {
            val byKey = HashMap<String, PlacedFloatingDanmaku>()
            for (placed in previousPlaced) {
                if (placed.enterTimeMillis >= freezeBeforeMillis) continue
                if (placed.trackIndex >= params.floatingTrackCount) continue
                val key = danmakuLayoutKey(placed.presentation)
                if (key !in newKeys) continue
                byKey[key] = placed
                frozenQueues[placed.trackIndex].addLast(placed)
            }
            frozenByKey = byKey
        }

        fun place(presentation: DanmakuPresentation): Boolean {
            if (params.floatingTrackCount == 0) return false

            val frozen = frozenByKey[danmakuLayoutKey(presentation)]
            if (frozen != null) {
                // 冻结的弹幕逐条按原布局复用
                frozenQueues[frozen.trackIndex].remove(frozen)
                lastPlaced[frozen.trackIndex] = frozen
                result.add(frozen)
                return true
            }

            val widthPx = measureWidth(presentation).coerceAtLeast(1)
            val speed = speedFor(presentation, widthPx)
            val enter = presentation.danmaku.playTimeMillis
            val exit = enter +
                    ((params.trackWidthPx + widthPx + params.safeSeparationPx) / speed * 1000.0).toLong()

            for (trackIndex in 0 until params.floatingTrackCount) {
                val upcoming = PlacedFloatingDanmaku(presentation, trackIndex, enter, widthPx, speed, exit)
                val fitsAfterLast = lastPlaced[trackIndex].let { it == null || fits(it, upcoming) }
                if (!fitsAfterLast) continue
                // 也不能与该轨道上已冻结的、进入时刻更晚的弹幕冲突
                val fitsBeforeFrozen = frozenQueues[trackIndex].firstOrNull().let { it == null || fits(upcoming, it) }
                if (!fitsBeforeFrozen) continue

                lastPlaced[trackIndex] = upcoming
                result.add(upcoming)
                return true
            }
            return false
        }

        private fun speedFor(presentation: DanmakuPresentation, widthPx: Int): Float {
            val baseMultiplier = params.speedMultiplier
                .pow(log(widthPx.toFloat() / params.baseSpeedTextWidthPx, 2f))
                .coerceAtLeast(1f)
            val fluctuation =
                (stableHash01(danmakuLayoutKey(presentation)) - 0.5f) * 2f * params.speedFluctuation
            val wallSpeed = params.baseSpeedPxPerSecond * (baseMultiplier + fluctuation)
            return wallSpeed / params.playbackSpeed
        }

        /**
         * [prev] 在前 [next] 在后 (enter 更晚) 时, 两者是否不冲突.
         */
        private fun fits(prev: PlacedFloatingDanmaku, next: PlacedFloatingDanmaku): Boolean {
            // 1. next 出现时 prev 已完全进入轨道 (含安全间隔)
            val prevFullyEnteredMillis = (prev.widthPx + params.safeSeparationPx) / prev.speedPxPerVideoSecond * 1000.0
            if ((next.enterTimeMillis - prev.enterTimeMillis) < prevFullyEnteredMillis) return false
            // 2. 不追尾: next 左边缘到达轨道左侧不早于 prev 完全滚出
            val nextLeftArrivalMillis = next.enterTimeMillis +
                    params.trackWidthPx / next.speedPxPerVideoSecond * 1000.0
            return prev.exitTimeMillis <= nextLeftArrivalMillis
        }
    }

    private class FixedPass(
        private val params: DanmakuLayoutParams,
        private val trackCount: Int,
    ) {
        val result = ArrayList<PlacedFixedDanmaku>()

        /** 每轨道最后一条弹幕的消失时刻 */
        private val lastEndMillis = LongArray(trackCount) { Long.MIN_VALUE }

        /** 每轨道尚未复用的冻结弹幕, 按进入时刻升序 */
        private val frozenQueues = Array(trackCount) { ArrayDeque<PlacedFixedDanmaku>() }

        private var frozenByKey: Map<String, PlacedFixedDanmaku> = emptyMap()

        /**
         * 显示时长为墙钟时间, 转换到视频时间轴.
         */
        private val durationVideoMillis: Long =
            (params.fixedDanmakuDurationMillis * params.playbackSpeed).toLong()

        fun seedFrozen(
            previousPlaced: List<PlacedFixedDanmaku>,
            freezeBeforeMillis: Long,
            newKeys: Set<String>,
        ) {
            val byKey = HashMap<String, PlacedFixedDanmaku>()
            for (placed in previousPlaced) {
                if (placed.enterTimeMillis >= freezeBeforeMillis) continue
                if (placed.trackIndex >= trackCount) continue
                val key = danmakuLayoutKey(placed.presentation)
                if (key !in newKeys) continue
                byKey[key] = placed
                frozenQueues[placed.trackIndex].addLast(placed)
            }
            frozenByKey = byKey
        }

        fun place(presentation: DanmakuPresentation): Boolean {
            if (trackCount == 0) return false

            val frozen = frozenByKey[danmakuLayoutKey(presentation)]
            if (frozen != null) {
                frozenQueues[frozen.trackIndex].remove(frozen)
                lastEndMillis[frozen.trackIndex] = frozen.endTimeMillis
                result.add(frozen)
                return true
            }

            val enter = presentation.danmaku.playTimeMillis
            val end = enter + durationVideoMillis

            for (trackIndex in 0 until trackCount) {
                if (lastEndMillis[trackIndex] > enter) continue
                val nextFrozen = frozenQueues[trackIndex].firstOrNull()
                if (nextFrozen != null && end > nextFrozen.enterTimeMillis) continue

                lastEndMillis[trackIndex] = end
                result.add(PlacedFixedDanmaku(presentation, trackIndex, enter, end))
                return true
            }
            return false
        }
    }
}

/**
 * 弹幕的全局唯一 key. 弹幕 id 仅保证服务内唯一, 因此加上 serviceId.
 */
internal fun danmakuLayoutKey(presentation: DanmakuPresentation): String =
    "${presentation.danmaku.serviceId.value}:${presentation.danmaku.id}"

/**
 * 稳定的字符串哈希, 映射到 `[0, 1)`. 跨平台、跨进程一致 (FNV-1a 64).
 */
internal fun stableHash01(s: String): Float {
    var hash = -3750763034362895579L // FNV-1a 64 offset basis
    for (c in s) {
        hash = hash xor c.code.toLong()
        hash *= 1099511628211L // FNV-1a 64 prime
    }
    val bits = (hash ushr 11) and ((1L shl 53) - 1)
    return (bits.toDouble() / (1L shl 53).toDouble()).toFloat()
}
