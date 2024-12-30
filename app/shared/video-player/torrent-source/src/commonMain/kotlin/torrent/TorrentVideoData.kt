/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.torrent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.averageRate
import org.openani.mediamp.internal.MediampInternalApi
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.NetStats
import kotlin.coroutines.CoroutineContext

interface MediaDataWithFileName : MediaData {
    val filename: String
}

val MediaData.filenameOrNull get() = (this as? MediaDataWithFileName)?.filename

class TorrentVideoData(
    private val handle: TorrentFileHandle,
) : MediaData, MediaDataWithFileName {
    private inline val entry get() = handle.entry
    override val filename: String get() = entry.pathInTorrent

    override fun fileLength(): Long = entry.length

    @OptIn(MediampInternalApi::class)
    override val networkStats: Flow<NetStats> =
        handle.entry.fileStats.map { it.downloadedBytes }.averageRate().map { downloadSpeed ->
            NetStats(
                downloadSpeed = downloadSpeed,
                uploadRate = -1,
            )
        }

    val pieces get() = handle.entry.pieces
    override val isCacheFinished get() = handle.entry.fileStats.map { it.isDownloadFinished }

    override suspend fun createInput(coroutineContext: CoroutineContext): org.openani.mediamp.io.SeekableInput =
        entry.createInput(coroutineContext)

    override suspend fun close() {
        handle.close()
    }

    override fun toString(): String {
        return "TorrentVideoData(entry=$entry)"
    }
}