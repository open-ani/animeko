/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.details

import androidx.compose.runtime.Immutable
import me.him188.ani.app.domain.media.cache.DownloaderStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState

/**
 * 详情页的下载器区块: 记录状态、传输统计与引擎内部状态.
 */
@Immutable
data class DownloaderDetails(
    val cacheState: MediaCacheState,
    val stats: MediaCache.SessionStats,
    val status: DownloaderStatus?,
)
