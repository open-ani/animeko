/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.media.player.BufferingMediaCacheProgressProvider
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.app.domain.media.player.TorrentMediaCacheProgressProvider
import me.him188.ani.app.domain.media.player.buildRangeCacheProgressInfo
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.app.domain.media.player.prefetch.MediaPrefetchController
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.media.player.prefetch.PrefetchSegmentInfo
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.source.UriMediaData
import kotlin.math.roundToInt

/**
 * 为当前播放的媒体提供进度条上显示的缓冲进度.
 *
 * - BT 数据源: 按 piece 的下载状态显示 ([TorrentMediaCacheProgressProvider]).
 * - 在线数据源 ([UriMediaData], 由播放器自行下载): 按播放器上报的已缓冲位置显示 ([BufferingMediaCacheProgressProvider]).
 *   播放器不支持 [Buffering] 时输出 [MediaCacheProgressInfo.Empty].
 *   若有预缓存进度 ([prefetchProgress], 如自动跳过 OP/ED 前提前下载的分片), 会与缓冲位置合并显示.
 * - 其他 (如本地文件): 输出 [MediaCacheProgressInfo.Empty], 视为已全部可用.
 */
class CacheProgressProvider(
    playerState: MediampPlayer,
    flowScope: CoroutineScope,
    /**
     * 额外的预缓存进度 (如 HLS 代理提前下载的分片), 会与播放器上报的缓冲位置合并显示. 见 [MediaPrefetchController].
     */
    prefetchProgress: Flow<List<PrefetchSegmentInfo>> = flowOf(emptyList()),
) {
    @OptIn(ExperimentalMediampApi::class)
    val cacheProgressInfoFlow = playerState.mediaData
        .flatMapLatest { data ->
            when (data) {
                is TorrentMediaData -> TorrentMediaCacheProgressProvider(data.pieces).flow
                is UriMediaData -> {
                    val buffering = playerState.features[Buffering]
                    val durationMillis = playerState.mediaProperties.map { it?.durationMillis }
                    if (buffering == null) {
                        prefetchOnlyProgress(prefetchProgress, durationMillis)
                    } else {
                        prefetchProgress.flatMapLatest { prefetch ->
                            if (prefetch.isEmpty()) {
                                BufferingMediaCacheProgressProvider(
                                    bufferedPositionMillis = buffering.bufferedPositionMillis,
                                    durationMillis = durationMillis,
                                ).flow
                            } else {
                                combine(buffering.bufferedPositionMillis, durationMillis) { buffered, duration ->
                                    mergeBufferedAndPrefetched(buffered, duration, prefetch)
                                }.distinctUntilChanged()
                            }
                        }
                    }
                }

                else -> flowOf(MediaCacheProgressInfo.Empty)
            }
        }.shareIn(
            flowScope,
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )
}

/**
 * 播放器不上报缓冲位置时, 只显示预缓存进度.
 */
private fun prefetchOnlyProgress(
    prefetchProgress: Flow<List<PrefetchSegmentInfo>>,
    durationMillis: Flow<Long?>,
): Flow<MediaCacheProgressInfo> = combine(prefetchProgress, durationMillis) { prefetch, duration ->
    if (prefetch.isEmpty() || duration == null || duration <= 0) {
        MediaCacheProgressInfo.Empty
    } else {
        buildRangeCacheProgressInfo(duration, prefetch)
    }
}.distinctUntilChanged()

/**
 * 把 `[0, buffered]` (已缓冲) 与预缓存的分片合并成进度条信息. 时长未知时返回 [MediaCacheProgressInfo.Empty].
 */
internal fun mergeBufferedAndPrefetched(
    bufferedPositionMillis: Long,
    durationMillis: Long?,
    prefetch: List<PrefetchSegmentInfo>,
): MediaCacheProgressInfo {
    if (durationMillis == null || durationMillis <= 0) return MediaCacheProgressInfo.Empty
    val ranges = ArrayList<PrefetchSegmentInfo>(prefetch.size + 1)
    if (bufferedPositionMillis > 0) {
        // 与 BufferingMediaCacheProgressProvider 一致, 量化到千分之一以避免高频重绘
        val quantized = (bufferedPositionMillis.toDouble() / durationMillis * BUFFERED_RATIO_STEPS).roundToInt()
            .coerceIn(0, BUFFERED_RATIO_STEPS)
        val quantizedMillis = durationMillis * quantized / BUFFERED_RATIO_STEPS
        ranges += PrefetchSegmentInfo(MediaTimeRange(0, quantizedMillis), ChunkState.DONE)
    }
    ranges += prefetch
    return buildRangeCacheProgressInfo(durationMillis, ranges)
}

private const val BUFFERED_RATIO_STEPS = 1000
