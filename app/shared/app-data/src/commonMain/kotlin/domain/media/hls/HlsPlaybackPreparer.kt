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
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.source.UriMediaData

interface HlsPlaybackPreparer {
    suspend fun prepare(
        data: UriMediaData,
        options: HlsPlaybackPrepareOptions = HlsPlaybackPrepareOptions(enableSegmentFiltering = true, enablePausePrefetch = false),
    ): HlsPlaybackPreparerResult
}

data class HlsPlaybackPrepareOptions(
    val enableSegmentFiltering: Boolean,
    val enablePausePrefetch: Boolean,
)

data class HlsPlaybackPreparerResult(
    val data: UriMediaData,
    val session: HlsPlaybackProxySession? = null,
)

interface HlsPlaybackProxySession : AutoCloseable {
    val cacheProgressInfoFlow: Flow<MediaCacheProgressInfo>
        get() = flowOf(MediaCacheProgressInfo.Empty)

    fun onPlaybackStateChanged(state: PlaybackState) {}
}

object NoopHlsPlaybackPreparer : HlsPlaybackPreparer {
    override suspend fun prepare(data: UriMediaData, options: HlsPlaybackPrepareOptions): HlsPlaybackPreparerResult {
        return HlsPlaybackPreparerResult(data)
    }
}
