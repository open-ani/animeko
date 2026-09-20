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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.media.player.BufferingMediaCacheProgressProvider
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.app.domain.media.player.TorrentMediaCacheProgressProvider
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.source.UriMediaData

/**
 * 为当前播放的媒体提供进度条上显示的缓冲进度.
 *
 * - BT 数据源: 按 piece 的下载状态显示 ([TorrentMediaCacheProgressProvider]).
 * - 在线数据源 ([UriMediaData], 由播放器自行下载): 按播放器上报的已缓冲位置显示 ([BufferingMediaCacheProgressProvider]).
 *   播放器不支持 [Buffering] 时输出 [MediaCacheProgressInfo.Empty].
 * - 其他 (如本地文件): 输出 [MediaCacheProgressInfo.Empty], 视为已全部可用.
 */
class CacheProgressProvider(
    playerState: MediampPlayer,
    flowScope: CoroutineScope,
) {
    @OptIn(ExperimentalMediampApi::class)
    val cacheProgressInfoFlow = playerState.mediaData
        .flatMapLatest { data ->
            when (data) {
                is TorrentMediaData -> TorrentMediaCacheProgressProvider(data.pieces).flow
                is UriMediaData -> {
                    val buffering = playerState.features[Buffering]
                    if (buffering == null) {
                        flowOf(MediaCacheProgressInfo.Empty)
                    } else {
                        BufferingMediaCacheProgressProvider(
                            bufferedPositionMillis = buffering.bufferedPositionMillis,
                            durationMillis = playerState.mediaProperties.map { it?.durationMillis },
                        ).flow
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
