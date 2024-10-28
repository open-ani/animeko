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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.ITorrentSessionStatsCallback
import me.him188.ani.app.domain.torrent.parcel.PTorrentSessionStats
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.peer.PeerInfo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RemoteTorrentSession(
    private val remote: IRemoteTorrentSession
) : TorrentSession {
    override val sessionStats: Flow<TorrentSession.Stats?>
        get() = callbackFlow {
            val disposable = remote.getSessionStats(object : ITorrentSessionStatsCallback.Stub() {
                override fun onEmit(stat: PTorrentSessionStats?) {
                    if (stat != null) trySend(stat.toStats())
                }
            })

            awaitClose { disposable.dispose() }
        }

    override suspend fun getName(): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.name
                cont.resume(result)
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override suspend fun getFiles(): List<TorrentFileEntry> {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.files
                cont.resume(RemoteTorrentFileEntryList(result))
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override fun getPeers(): List<PeerInfo> {
        return remote.peers.asList()
    }

    override suspend fun close() {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.close()
                cont.resume(result)
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override suspend fun closeIfNotInUse() {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.closeIfNotInUse()
                cont.resume(result)
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }
}