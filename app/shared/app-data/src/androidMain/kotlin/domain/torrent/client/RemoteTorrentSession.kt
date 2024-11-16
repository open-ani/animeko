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
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.callback.ITorrentSessionStatsCallback
import me.him188.ani.app.domain.torrent.cont.ContTorrentSessionGetFiles
import me.him188.ani.app.domain.torrent.parcel.PTorrentSessionStats
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.utils.coroutines.IO_

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentSession(
    private val fetchRemoteScope: CoroutineScope,
    connectivityAware: ConnectivityAware,
    getRemote: suspend () -> IRemoteTorrentSession
) : TorrentSession,
    RemoteCall<IRemoteTorrentSession> by RetryRemoteCall(fetchRemoteScope, getRemote),
    ConnectivityAware by connectivityAware {
    override val sessionStats: Flow<TorrentSession.Stats?> = callbackFlow {
        var disposable: IDisposableHandle? = null
        val callback = object : ITorrentSessionStatsCallback.Stub() {
            override fun onEmit(stat: PTorrentSessionStats?) {
                if (stat != null) trySend(stat.toStats())
            }
        }

        // todo: not thread-safe
        disposable = call { getSessionStats(callback) }
        val transform = registerStateTransform(false, true) {
            disposable?.callOnceOrNull { dispose() }
            disposable = call { getSessionStats(callback) }
        }

        awaitClose {
            disposable?.callOnceOrNull { dispose() }
            unregister(transform)
        }
    }

    override suspend fun getName(): String {
        return withContext(Dispatchers.IO_) { call { name } }
    }

    override suspend fun getFiles(): List<TorrentFileEntry> {
        return RemoteTorrentFileEntryList(fetchRemoteScope, this@RemoteTorrentSession) {
            callSuspendCancellable { resolve, reject ->
                getFiles(
                    object : ContTorrentSessionGetFiles.Stub() {
                        override fun resume(value: IRemoteTorrentFileEntryList?) = resolve(value)
                        override fun resumeWithException(exception: RemoteContinuationException?) =
                            reject(exception)
                    },
                )
            }
        }
    }

    override fun getPeers(): List<PeerInfo> {
        return call { peers }.asList()
    }

    override suspend fun close() {
        withContext(Dispatchers.IO_) {
            call { close() }
        }
    }

    override suspend fun closeIfNotInUse() {
        withContext(Dispatchers.IO_) {
            call { closeIfNotInUse() }
        }
    }
}