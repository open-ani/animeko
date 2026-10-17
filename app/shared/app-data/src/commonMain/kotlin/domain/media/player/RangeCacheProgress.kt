/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player

import androidx.collection.MutableFloatList
import me.him188.ani.app.domain.media.player.prefetch.PrefetchSegmentInfo

/**
 * 由若干带状态的时间范围构造进度条的缓存进度信息.
 *
 * 范围可以重叠, 重叠处取 "更完成" 的状态 ([ChunkState.DONE] > [ChunkState.DOWNLOADING] > [ChunkState.NOT_AVAILABLE] > [ChunkState.NONE]).
 * 未被任何范围覆盖的部分为 [ChunkState.NONE]. 相邻同状态的区块会合并.
 *
 * @param durationMillis 媒体总时长. 非正数时返回 [MediaCacheProgressInfo.Empty].
 */
fun buildRangeCacheProgressInfo(
    durationMillis: Long,
    ranges: List<PrefetchSegmentInfo>,
): MediaCacheProgressInfo {
    if (durationMillis <= 0L) return MediaCacheProgressInfo.Empty
    val clamped = ranges.mapNotNull { segment ->
        val start = segment.range.startMillis.coerceIn(0L, durationMillis)
        val end = segment.range.endMillis.coerceIn(0L, durationMillis)
        if (end <= start) null else Triple(start, end, segment.state)
    }
    if (clamped.isEmpty()) {
        return MediaCacheProgressInfo(
            chunkWeights = androidx.collection.floatListOf(1f),
            chunkStates = listOf(ChunkState.NONE),
        )
    }
    val boundaries = buildSet {
        add(0L)
        add(durationMillis)
        for ((start, end, _) in clamped) {
            add(start)
            add(end)
        }
    }.sorted()

    val weights = MutableFloatList(boundaries.size)
    val states = ArrayList<ChunkState>(boundaries.size)
    for (i in 0 until boundaries.lastIndex) {
        val a = boundaries[i]
        val b = boundaries[i + 1]
        var state = ChunkState.NONE
        for ((start, end, s) in clamped) {
            if (start <= a && b <= end && s.priority > state.priority) state = s
        }
        val weight = (b - a).toFloat() / durationMillis
        if (states.isNotEmpty() && states.last() == state) {
            weights[weights.lastIndex] = weights[weights.lastIndex] + weight
        } else {
            weights.add(weight)
            states.add(state)
        }
    }
    return MediaCacheProgressInfo(chunkWeights = weights, chunkStates = states)
}

private val ChunkState.priority: Int
    get() = when (this) {
        ChunkState.NONE -> 0
        ChunkState.NOT_AVAILABLE -> 1
        ChunkState.DOWNLOADING -> 2
        ChunkState.DONE -> 3
    }
