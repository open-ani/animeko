/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.DeadObjectException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.ITorrentSessionStatsCallback
import me.him188.ani.app.domain.torrent.client.ConnectivityAware
import me.him188.ani.app.domain.torrent.parcel.PPeerInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentSessionStats
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.utils.coroutines.CancellationException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TorrentSessionProxy(
    private val delegate: TorrentSession,
    connectivityAware: ConnectivityAware,
    context: CoroutineContext
) : IRemoteTorrentSession.Stub(), ConnectivityAware by connectivityAware {
    private val scope = context.childScope()
    private val logger = logger<TorrentSessionProxy>()
    
    override fun getSessionStats(flow: ITorrentSessionStatsCallback?): IDisposableHandle {
        val job = scope.launch(Dispatchers.IO_) { 
            delegate.sessionStats.collect {
                if (!isConnected) return@collect
                if (it == null) return@collect

                try {
                    flow?.onEmit(
                        PTorrentSessionStats(
                            it.totalSizeRequested,
                            it.downloadedBytes,
                            it.downloadSpeed,
                            it.uploadedBytes,
                            it.uploadSpeed,
                            it.downloadProgress,
                        ),
                    )
                } catch (doe: DeadObjectException) {
                    throw CancellationException("Cancelled collecting session stats.", doe)
                }
            }
        }
        
        return DisposableHandleProxy { job.cancel() }
    }

    override fun getName(): String {
        return runBlocking { delegate.getName() }
    }

    override fun getFiles(): IRemoteTorrentFileEntryList {
        val list = runBlocking { delegate.getFiles() }

        return TorrentFileEntryListProxy(list, this, scope.coroutineContext)
    }

    override fun getPeers(): Array<PPeerInfo> {
        return runBlocking { 
            delegate.getPeers().map { 
                PPeerInfo(
                    it.handle,
                    it.id,
                    it.client,
                    it.ipAddr,
                    it.ipPort,
                    it.progress,
                    it.totalDownload.inBytes,
                    it.totalUpload.inBytes,
                    it.flags
                ) 
            }.toTypedArray()
        }
    }

    override fun close() {
        return runBlocking { delegate.close() }
    }

    override fun closeIfNotInUse() {
        return runBlocking { delegate.closeIfNotInUse() }
    }
}