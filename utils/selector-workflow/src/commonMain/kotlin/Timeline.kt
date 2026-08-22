/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import androidx.compose.runtime.Immutable
import me.him188.ani.utils.selectorworkflow.anim.Track
import kotlin.time.Duration

/**
 * 编译好的时间线. 每个单元一组轨道, 采样即得 [SelectorWorkflowState].
 *
 * 时间线是纯数据、可重复采样的 —— 同一个 [Duration] 采出来永远是同一份状态,
 * 所以拖进度条、单帧截图、单元测试都能直接用.
 */
@Immutable
class SelectorWorkflowTimeline internal constructor(
    val config: SelectorWorkflowConfig,
    val duration: Duration,
    internal val phase: Track<String>,
    internal val nodes: List<NodeTracks>,
    internal val links: List<LineTracks>,
    internal val chips: List<ChipTracks>,
    internal val ripples: List<RippleTracks>,
    internal val cursors: List<CursorTracks>,
    internal val handoff: LineTracks,
    internal val window: Track<WindowTone>,
    internal val rows: List<RowTracks>,
    internal val scroll: Track<Float>,
    internal val clocks: Map<ClockId, ClockTracks>,
) {
    fun sampleAt(time: Duration): SelectorWorkflowState {
        val t = time.coerceIn(Duration.ZERO, duration)
        return SelectorWorkflowState(
            time = t,
            duration = duration,
            phase = phase.valueAt(t),
            sourceNodes = nodes.map { it.sample(t) },
            sourceLinks = links.map { it.sample(t) },
            results = chips.map { it.sample(t) },
            ripples = ripples.map { it.sample(t) },
            cursors = cursors.map { it.sample(t) },
            handoff = handoff.sample(t),
            window = WindowState(window.valueAt(t)),
            requestRows = rows.map { it.sample(t) },
            scroll = ScrollState(scroll.valueAt(t)),
            clocks = clocks.mapValues { (_, tracks) -> tracks.sample(t) },
        )
    }
}

internal class NodeTracks(
    private val index: Int,
    private val alpha: Track<Float>,
    private val pulsing: Track<Boolean>,
    private val priority: Boolean,
) {
    fun sample(t: Duration) = SourceNodeState(index, alpha.valueAt(t), pulsing.valueAt(t), priority)
}

internal class LineTracks(
    private val progress: Track<Float>,
    private val alpha: Track<Float>,
) {
    fun sample(t: Duration) = LineState(progress.valueAt(t), alpha.valueAt(t))
}

internal class ChipTracks(
    private val key: ResultKey,
    private val cell: Int,
    private val candidate: Boolean,
    private val priority: Boolean,
    private val alpha: Track<Float>,
    private val tone: Track<ChipTone>,
    private val scale: Track<Float>,
) {
    fun sample(t: Duration) = ResultChipState(
        key = key,
        cell = cell,
        alpha = alpha.valueAt(t),
        tone = tone.valueAt(t),
        scale = scale.valueAt(t),
        candidate = candidate,
        priority = priority,
    )
}

internal class RippleTracks(
    private val target: RippleTarget,
    private val index: Int,
    private val scale: Track<Float>,
    private val alpha: Track<Float>,
) {
    fun sample(t: Duration) = RippleState(target, index, scale.valueAt(t), alpha.valueAt(t))
}

internal class CursorTracks(
    private val id: String,
    private val owner: Int?,
    private val cell: Track<Float>,
    private val alpha: Track<Float>,
) {
    fun sample(t: Duration) = CursorState(id, owner, cell.valueAt(t), alpha.valueAt(t))
}

internal class RowTracks(
    private val index: Int,
    private val alpha: Track<Float>,
    private val icon: Track<RequestIcon>,
    private val tone: Track<RequestTone>,
    private val iconScale: Track<Float>,
) {
    fun sample(t: Duration) = RequestRowState(
        index, alpha.valueAt(t), icon.valueAt(t), tone.valueAt(t), iconScale.valueAt(t),
    )
}

internal class ClockTracks(
    private val id: ClockId,
    private val alpha: Track<Float>,
    private val sweep: Track<Float>,
    private val tone: Track<ClockTone>,
    private val overlay: Track<Float>,
    /** 这个表数的是设置里配的多少秒. 整条时间线上是个常量, 不需要轨道. */
    private val budgetSeconds: Float,
) {
    fun sample(t: Duration) = ClockState(
        id = id,
        alpha = alpha.valueAt(t),
        sweep = sweep.valueAt(t),
        tone = tone.valueAt(t),
        overlayAlpha = overlay.valueAt(t),
        budgetSeconds = budgetSeconds,
    )
}
