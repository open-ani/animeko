/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.Build
import android.os.DeadObjectException
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry
import me.him188.ani.app.domain.torrent.callback.ITorrentFileEntryStatsCallback
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryGetInputParams
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryResolveFile
import me.him188.ani.app.domain.torrent.parcel.PTorrentFileEntryStats
import me.him188.ani.app.domain.torrent.parcel.PTorrentInputParameter
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toFile
import java.io.RandomAccessFile

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentFileEntry(
    private val fetchRemoteScope: CoroutineScope,
    private val remote: RemoteCall<IRemoteTorrentFileEntry>,
    private val connectivityAware: ConnectivityAware
) : TorrentFileEntry {
    override val fileStats: Flow<TorrentFileEntry.Stats> = callbackFlow {
        var disposable: IDisposableHandle? = null
        val callback = object : ITorrentFileEntryStatsCallback.Stub() {
            override fun onEmit(stat: PTorrentFileEntryStats?) {
                if (stat != null) trySend(stat.toStats())
            }
        }

        // todo: not thread-safe
        disposable = remote.call { getFileStats(callback) }
        val transform = connectivityAware.registerStateTransform(false, true) {
            try {
                disposable?.dispose()
            } catch (_: DeadObjectException) {
            }
            disposable = remote.call { getFileStats(callback) }
        }

        awaitClose {
            try {
                disposable?.dispose()
            } catch (_: DeadObjectException) {
            }
            connectivityAware.unregister(transform)
        }
    }

    override val length: Long get() = remote.call { length }

    override val pathInTorrent: String get() = remote.call { pathInTorrent }

    override val pieces: PieceList get() = RemotePieceList(connectivityAware, remote.call { pieces })

    override val supportsStreaming: Boolean get() = remote.call { supportsStreaming }

    override fun createHandle(): TorrentFileHandle {
        return RemoteTorrentFileHandle(
            fetchRemoteScope,
            RetryRemoteCall(fetchRemoteScope) { remote.call { createHandle() } },
            connectivityAware,
        )
    }

    override suspend fun resolveFile(): SystemPath =
        remote.callSuspendCancellable { resolve, reject ->
            resolveFile(
                object : ContTorrentFileEntryResolveFile.Stub() {
                    override fun resume(value: String?) = resolve(value?.let { Path(it).inSystem })
                    override fun resumeWithException(exception: RemoteContinuationException?) = reject(exception)
                },
            )
        }

    override fun resolveFileMaybeEmptyOrNull(): SystemPath? {
        val result = remote.call { resolveFileMaybeEmptyOrNull() }
        return if (result != null) Path(result).inSystem else null
    }

    override suspend fun createInput(): SeekableInput =
        remote.callSuspendCancellable { resolve, reject ->
            getTorrentInputParams(
                object : ContTorrentFileEntryGetInputParams.Stub() {
                    override fun resume(value: PTorrentInputParameter?) {
                        if (value == null) {
                            resolve(null)
                            return
                        }

                        TorrentInput(
                            file = RandomAccessFile(Path(value.file).inSystem.toFile(), "r"),
                            pieces = this@RemoteTorrentFileEntry.pieces,
                            logicalStartOffset = value.logicalStartOffset,
                            onWait = {
                                withContext(Dispatchers.IO_) {
                                    remote.call { torrentInputOnWait(it.pieceIndex) }
                                }
                            },
                            bufferSize = value.bufferSize,
                            size = value.size,
                        ).also { resolve(it) }
                    }

                    override fun resumeWithException(exception: RemoteContinuationException?) = reject(exception)
                },
            )
        }
}