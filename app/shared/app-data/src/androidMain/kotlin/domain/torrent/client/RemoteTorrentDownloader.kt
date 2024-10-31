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
import android.os.RemoteException
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.ITorrentDownloaderStatsCallback
import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentDownloaderStats
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentDownloader(
    private val remote: IRemoteTorrentDownloader
) : TorrentDownloader {
    override val totalStats: Flow<TorrentDownloader.Stats>
        get() = callbackFlow {
            val disposable = remote.getTotalStatus(object : ITorrentDownloaderStatsCallback.Stub() {
                override fun onEmit(stat: PTorrentDownloaderStats?) {
                    if (stat != null) trySend(stat.toStats())
                }
            })
            
            awaitClose { disposable.dispose() }
        }

    override val vendor: TorrentLibInfo = remote.vendor.toTorrentLibInfo()

    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.fetchTorrent(uri, timeoutSeconds)
                cont.resume(result.toEncodedTorrentInfo())
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
        overrideSaveDir: SystemPath?
    ): TorrentSession {
        return suspendCancellableCoroutine { cont ->
            try {
                val result = remote.startDownload(PEncodedTorrentInfo(data.data), overrideSaveDir?.absolutePath)
                cont.resume(RemoteTorrentSession(result))
            } catch (re: RemoteException) {
                cont.resumeWithException(re)
            }
        }
    }

    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): SystemPath {
        val remotePath = remote.getSaveDirForTorrent(PEncodedTorrentInfo(data.data))
        return Path(remotePath).inSystem
    }

    override fun listSaves(): List<SystemPath> {
        return remote.listSaves().map { Path(it).inSystem }
    }

    override fun close() {
        return remote.close()
    }
}