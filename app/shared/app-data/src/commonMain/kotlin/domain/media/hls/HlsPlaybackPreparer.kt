/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.media.player.prefetch.PrefetchSegmentInfo
import org.openani.mediamp.source.UriMediaData

/**
 * HLS 本地代理的功能开关.
 */
data class HlsPlaybackOptions(
    /**
     * 过滤疑似广告分片 (实验性).
     */
    val filterSegments: Boolean = false,
    /**
     * 通过本地代理转发媒体分片, 以支持提前缓存指定时间范围 ([HlsPlaybackProxySession.setPrefetchRange]).
     */
    val proxySegments: Boolean = false,
) {
    val isEnabled: Boolean get() = filterSegments || proxySegments

    companion object {
        val Disabled = HlsPlaybackOptions()
    }
}

interface HlsPlaybackPreparer {
    /**
     * @param startPositionHintMillis 播放预计从哪里开始 (续播时为记忆的进度), 在去除广告后的时间轴上.
     * 只用于决定起播前预先下载哪个分片, 不影响播放列表; 给错了只会白下载一个分片.
     * 它是单次播放的输入, 不是用户开关, 所以不放进 [HlsPlaybackOptions].
     */
    suspend fun prepare(
        data: UriMediaData,
        options: HlsPlaybackOptions,
        startPositionHintMillis: Long? = null,
    ): HlsPlaybackPreparerResult
}

data class HlsPlaybackPreparerResult(
    val data: UriMediaData,
    val session: HlsPlaybackProxySession? = null,
)

interface HlsPlaybackProxySession : AutoCloseable {
    /**
     * 请求提前下载 [range] 内的分片, 替换之前的请求. 传 `null` 取消. 未启用分片代理时忽略.
     */
    fun setPrefetchRange(range: MediaTimeRange?) {}

    /**
     * 预缓存分片的进度. 未启用分片代理时始终为空列表.
     */
    val prefetchProgress: Flow<List<PrefetchSegmentInfo>> get() = flowOf(emptyList())
}

object NoopHlsPlaybackPreparer : HlsPlaybackPreparer {
    override suspend fun prepare(
        data: UriMediaData,
        options: HlsPlaybackOptions,
        startPositionHintMillis: Long?,
    ): HlsPlaybackPreparerResult {
        return HlsPlaybackPreparerResult(data)
    }
}
