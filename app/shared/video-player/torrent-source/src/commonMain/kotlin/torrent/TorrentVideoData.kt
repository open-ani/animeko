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
import me.him188.ani.app.videoplayer.data.VideoData
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.io.SeekableInput
import kotlin.coroutines.CoroutineContext

class TorrentVideoData(
    private val handle: TorrentFileHandle,
) : VideoData {
    private inline val entry get() = handle.entry
    override val filename: String get() = entry.pathInTorrent
    override val fileLength: Long get() = entry.length

    override fun computeHash(): String? = null

    override val networkStats: Flow<VideoData.Stats> =
        handle.entry.fileStats.map { it.downloadedBytes }.averageRate().map { downloadSpeed ->
            VideoData.Stats(
                downloadSpeed = downloadSpeed.bytes,
                uploadRate = FileSize.Unspecified,
            )
        }

    val pieces get() = handle.entry.pieces
    override val isCacheFinished get() = handle.entry.fileStats.map { it.isDownloadFinished }

    override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput =
        entry.createInput(coroutineContext)

    override suspend fun close() {
        handle.close()
    }

    override fun toString(): String {
        return "TorrentVideoData(entry=$entry)"
    }
}