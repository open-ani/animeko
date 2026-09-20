/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

/**
 * 把媒体时间范围按平均码率线性换算为 BT 文件内的字节范围 (相对文件开头, 闭区间).
 *
 * 视频通常是可变码率, 线性估算会有偏差, 因此在两侧各多取 [marginMillis] 作为余量. 这只是尽力而为的预缓存提示,
 * 估错了也只是多下载或少下载一些数据, 播放器真正 seek 过去时仍会按实际偏移请求.
 *
 * @return `null` 表示无法估算 (时长或文件大小未知) 或范围为空.
 */
internal fun estimateTorrentByteRange(
    range: MediaTimeRange,
    durationMillis: Long,
    fileLength: Long,
    marginMillis: Long = TORRENT_PREFETCH_MARGIN_MILLIS,
): LongRange? {
    if (durationMillis <= 0L || fileLength <= 0L) return null
    val startMillis = (range.startMillis - marginMillis).coerceIn(0L, durationMillis)
    val endMillis = (range.endMillis + marginMillis).coerceIn(0L, durationMillis)
    if (endMillis <= startMillis) return null
    val start = (startMillis.toDouble() / durationMillis * fileLength).toLong().coerceIn(0L, fileLength - 1)
    val end = (endMillis.toDouble() / durationMillis * fileLength).toLong().coerceIn(0L, fileLength - 1)
    if (end < start) return null
    return start..end
}

internal const val TORRENT_PREFETCH_MARGIN_MILLIS: Long = 10_000
