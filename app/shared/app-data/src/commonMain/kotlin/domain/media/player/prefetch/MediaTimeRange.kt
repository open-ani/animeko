/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

import me.him188.ani.app.domain.media.player.ChunkState

/**
 * 媒体时间轴上的一段范围, 单位毫秒, 半开区间 `[startMillis, endMillis)`.
 */
data class MediaTimeRange(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(endMillis >= startMillis) { "endMillis () must be >= startMillis ()" }
    }

    val durationMillis: Long get() = endMillis - startMillis
    val isEmpty: Boolean get() = endMillis == startMillis

    fun overlaps(other: MediaTimeRange): Boolean = startMillis < other.endMillis && other.startMillis < endMillis
}

/**
 * 预缓存的一个片段及其下载状态, 用于在进度条上显示预缓存进度.
 */
data class PrefetchSegmentInfo(
    val range: MediaTimeRange,
    val state: ChunkState,
)
