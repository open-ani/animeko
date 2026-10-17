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

/**
 * 一次预缓存请求.
 *
 * @property range 希望提前缓存的范围, 通常是即将自动跳过的章节结束后的一小段.
 * @property requireBufferedUntilMillis 启动预缓存的前提: 从当前播放位置到这个位置 (通常是章节开头) 的内容必须已经缓冲好.
 *   预缓存会与正常播放分享带宽. 满足这个前提时, 播放器之后要下载的都是即将被跳过的内容, 分走带宽不会影响观看;
 *   不满足 (网络慢, 缓冲跟不上) 时宁可不预缓存, 也不能让预缓存造成卡顿.
 */
data class MediaPrefetchRequest(
    val range: MediaTimeRange,
    val requireBufferedUntilMillis: Long,
)
