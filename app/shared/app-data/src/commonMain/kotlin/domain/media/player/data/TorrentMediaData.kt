/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.averageRate
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.SeekableInputMediaData
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMediampApi::class)
class TorrentMediaData(
    private val handle: TorrentFileHandle,
    private val onClose: () -> Unit,
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
    override val options: List<String> = emptyList(),
) : SeekableInputMediaData, DownloadingMediaData, FileMediaData {
    private inline val entry get() = handle.entry
    override val filename: String get() = entry.pathInTorrent
    override val uri: String get() = "torrent://dummy/${entry.pathInTorrent}"

    override fun fileLength(): Long = entry.length

    override val networkStats: Flow<NetStats> =
        handle.entry.fileStats.map { it.downloadedBytes }.averageRate().map { downloadSpeed ->
            NetStats(
                downloadSpeed = downloadSpeed,
                uploadRate = -1,
            )
        }

    val pieces get() = handle.entry.pieces
    override val isCacheFinished get() = handle.entry.fileStats.map { it.isDownloadFinished }

    override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput =
        entry.createInput(coroutineContext)

    override fun close() {
        onClose()
    }

    override fun toString(): String {
        return "TorrentMediaData(entry=$entry)"
    }
}