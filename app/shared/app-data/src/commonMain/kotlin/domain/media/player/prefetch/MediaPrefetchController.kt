/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

/**
 * 把 "提前缓存某段时间的媒体" 的请求分发到当前数据源:
 *
 * - BT ([TorrentMediaData]): 按平均码率换算成字节范围, 提高对应 piece 的下载优先级 ([estimateTorrentByteRange]).
 *   进度直接体现在 piece 状态上, 进度条已按 piece 显示, 无需额外上报.
 * - HLS (经本地代理播放的 [UriMediaData]): 交给 [HlsPlaybackProxySession] 提前下载对应分片, 进度由 [prefetchProgress] 上报.
 * - 其他数据源: 忽略.
 *
 * 请求与调用 [setPrefetchRange] 时正在播放的媒体绑定, 切换媒体后旧请求自动失效, 不会误作用到新媒体上.
 */
class MediaPrefetchController(
    private val player: MediampPlayer,
    hlsProxySession: Flow<HlsPlaybackProxySession?>,
    scope: CoroutineScope,
) {
    private class Request(val range: MediaTimeRange, val media: MediaData?)

    private val request = MutableStateFlow<Request?>(null)

    /**
     * 设置希望提前缓存的时间范围, 替换之前的请求. 传 `null` 取消.
     */
    fun setPrefetchRange(range: MediaTimeRange?) {
        request.value = range?.let { Request(it, player.mediaData.value) }
    }

    /**
     * 需要额外显示在进度条上的预缓存进度 (目前只有 HLS). BT 的进度已包含在 piece 状态中.
     */
    val prefetchProgress: Flow<List<PrefetchSegmentInfo>> = hlsProxySession.flatMapLatest { session ->
        session?.prefetchProgress ?: flowOf(emptyList())
    }

    private data class Target(
        val media: MediaData?,
        val durationMillis: Long?,
        val hlsSession: HlsPlaybackProxySession?,
        val range: MediaTimeRange?,
    )

    init {
        scope.launch {
            combine(
                player.mediaData,
                player.mediaProperties.map { it?.durationMillis }.distinctUntilChanged(),
                hlsProxySession,
                request,
            ) { media, duration, hls, request ->
                // 请求只对发出它时的媒体有效
                val range = request?.takeIf { it.media === media }?.range
                Target(media, duration, hls, range)
            }.distinctUntilChanged().collect { apply(it) }
        }
    }

    private suspend fun apply(target: Target) {
        when (val media = target.media) {
            is TorrentMediaData -> {
                val byteRange = target.range?.let { range ->
                    estimateTorrentByteRange(range, target.durationMillis ?: 0L, media.fileLength())
                }
                logger.info { "Torrent prefetch ${target.range} -> bytes $byteRange" }
                // Android 上是跨进程调用, 不要阻塞调用方的线程
                withContext(Dispatchers.IO_) { media.setPrefetchByteRange(byteRange) }
            }

            is UriMediaData -> target.hlsSession?.setPrefetchRange(target.range)
            else -> {}
        }
    }

    private companion object {
        private val logger = logger<MediaPrefetchController>()
    }
}
