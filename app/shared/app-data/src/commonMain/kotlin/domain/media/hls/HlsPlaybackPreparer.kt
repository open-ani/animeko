/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import org.openani.mediamp.source.UriMediaData

interface HlsPlaybackPreparer {
    suspend fun prepare(data: UriMediaData): HlsPlaybackPreparerResult
}

data class HlsPlaybackPreparerResult(
    val data: UriMediaData,
    val session: HlsPlaybackProxySession? = null,
)

interface HlsPlaybackProxySession : AutoCloseable

object NoopHlsPlaybackPreparer : HlsPlaybackPreparer {
    override suspend fun prepare(data: UriMediaData): HlsPlaybackPreparerResult {
        return HlsPlaybackPreparerResult(data)
    }
}
