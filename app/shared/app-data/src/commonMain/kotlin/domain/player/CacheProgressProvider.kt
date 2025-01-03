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
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.app.domain.media.player.TorrentMediaCacheProgressProvider
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import org.openani.mediamp.MediampPlayer

class CacheProgressProvider(
    playerState: MediampPlayer,
    flowScope: CoroutineScope,
) {
    val cacheProgressInfoFlow = playerState.mediaData
        .flatMapLatest { data ->
            when (data) {
                is TorrentMediaData -> TorrentMediaCacheProgressProvider(data.pieces).flow
                else -> flowOf(MediaCacheProgressInfo.Empty)
            }
        }.shareIn(
            flowScope,
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )
}
