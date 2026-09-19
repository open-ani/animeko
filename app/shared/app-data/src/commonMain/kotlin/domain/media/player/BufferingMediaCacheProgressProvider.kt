/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player

import androidx.collection.floatListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * 根据播放器上报的已缓冲位置生成缓冲进度, 用于在线 (HTTP/HLS) 数据源等由播放器自行下载的媒体.
 *
 * 播放器只知道从播放位置起连续缓冲到了哪个位置, 不保证之前的数据仍在缓存中.
 * 生成的进度信息把 `[0, bufferedPosition]` 标记为 [ChunkState.DONE], 其余为 [ChunkState.NONE].
 * 播放位置之前的区域在进度条上会被播放进度覆盖, 因此视觉效果即为 "已缓冲到的位置".
 *
 * 当播放器未上报缓冲位置或时长未知时, 输出 [MediaCacheProgressInfo.Empty].
 *
 * @param bufferedPositionMillis 已缓冲到的媒体位置, 单位毫秒; 负数表示未知.
 * @param durationMillis 媒体总时长, 单位毫秒; `null` 或非正数表示未知.
 */
class BufferingMediaCacheProgressProvider(
    bufferedPositionMillis: Flow<Long>,
    durationMillis: Flow<Long?>,
) : MediaCacheProgressProvider {
    override val flow: Flow<MediaCacheProgressInfo> =
        combine(bufferedPositionMillis, durationMillis) { buffered, duration ->
            if (buffered < 0 || duration == null || duration <= 0) {
                return@combine -1
            }
            // 量化到千分之一, 避免播放器高频上报缓冲位置时进度条无意义地重绘.
            (buffered.toDouble() / duration * RATIO_STEPS).roundToInt().coerceIn(0, RATIO_STEPS)
        }.distinctUntilChanged().map { quantized ->
            if (quantized < 0) MediaCacheProgressInfo.Empty
            else createInfo(quantized.toFloat() / RATIO_STEPS)
        }

    companion object {
        private const val RATIO_STEPS = 1000

        /**
         * 创建 `[0, bufferedRatio]` 为 [ChunkState.DONE], 其余为 [ChunkState.NONE] 的进度信息.
         */
        fun createInfo(bufferedRatio: Float): MediaCacheProgressInfo {
            val done = bufferedRatio.coerceIn(0f, 1f)
            return MediaCacheProgressInfo(
                chunkWeights = floatListOf(done, 1f - done),
                chunkStates = listOf(ChunkState.DONE, ChunkState.NONE),
            )
        }
    }
}
