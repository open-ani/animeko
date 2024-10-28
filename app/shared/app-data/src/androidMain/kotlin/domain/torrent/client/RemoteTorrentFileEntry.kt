/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.ITorrentFileEntryStatsCallback
import me.him188.ani.app.domain.torrent.parcel.PTorrentFileEntryStats
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toFile
import java.io.RandomAccessFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RemoteTorrentFileEntry(
    private val remote: IRemoteTorrentFileEntry
) : TorrentFileEntry {
    override val fileStats: Flow<TorrentFileEntry.Stats>
        get() = callbackFlow {
            val disposable = remote.getFileStats(object : ITorrentFileEntryStatsCallback.Stub() {
                override fun onEmit(stat: PTorrentFileEntryStats?) {
                    if (stat != null) trySend(stat.toStats())
                }
            })

            awaitClose { disposable.dispose() }
        }

    override val length: Long by lazy { remote.length }

    override val pathInTorrent: String by lazy { remote.pathInTorrent }

    override val pieces: PieceList by lazy { RemotePieceList(remote.pieces) }

    override val supportsStreaming: Boolean by lazy { remote.supportsStreaming }

    override fun createHandle(): TorrentFileHandle {
        return RemoteTorrentFileHandle(remote.createHandle())
    }

    override suspend fun resolveFile(): SystemPath {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.resolveFile()
                cont.resume(Path(result).inSystem)
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override fun resolveFileMaybeEmptyOrNull(): SystemPath? {
        val result = remote.resolveFileMaybeEmptyOrNull()
        return if (result != null) Path(result).inSystem else null
    }

    override suspend fun createInput(): SeekableInput {
        val remoteInput = remote.torrentInputParams

        val file = Path(remoteInput.file).inSystem
        
        return TorrentInput(
            file = withContext(Dispatchers.IO) { RandomAccessFile(file.toFile(), "r") },
            pieces = pieces,
            logicalStartOffset = remoteInput.logicalStartOffset,
            onWait = {
                suspendCancellableCoroutine { cont ->
                    cont.resume(remote.torrentInputOnWait(it.pieceIndex))
                }
            },
            bufferSize = remoteInput.bufferSize,
            size = remoteInput.size,
        )
    }
}