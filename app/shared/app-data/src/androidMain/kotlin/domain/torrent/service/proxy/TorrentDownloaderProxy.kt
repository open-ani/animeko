/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.ITorrentDownloaderStatsCallback
import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentDownloaderStats
import me.him188.ani.app.domain.torrent.parcel.PTorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import kotlin.coroutines.CoroutineContext

class TorrentDownloaderProxy(
    private val delegate: TorrentDownloader,
    context: CoroutineContext,
) : IRemoteTorrentDownloader.Stub(), CoroutineScope by context.childScope() {
    
    override fun getTotalStatus(flow: ITorrentDownloaderStatsCallback?): IDisposableHandle {
        val job = launch {
            delegate.totalStats.collect {
                flow?.onEmit(
                    PTorrentDownloaderStats(
                        it.totalSize,
                        it.downloadedBytes,
                        it.downloadSpeed,
                        it.uploadedBytes,
                        it.uploadSpeed,
                        it.downloadProgress
                    )
                )
            }
        }
        
        return DisposableHandleProxy { job.cancel() }
    }

    override fun getVendor(): PTorrentLibInfo {
        val vendor = delegate.vendor
        
        return PTorrentLibInfo(
            vendor.vendor,
            vendor.version,
            vendor.supportsStreaming
        )
    }

    override fun fetchTorrent(uri: String?, timeoutSeconds: Int): PEncodedTorrentInfo? {
        if (uri == null) return null
        
        val fetched = runBlocking { delegate.fetchTorrent(uri, timeoutSeconds) }
        return PEncodedTorrentInfo(fetched.data)
    }

    override fun startDownload(data: PEncodedTorrentInfo?, overrideSaveDir: String?): IRemoteTorrentSession? {
        if (data == null || overrideSaveDir == null) return null
        
        val session = runBlocking { 
            delegate.startDownload(
                EncodedTorrentInfo.createRaw(data.data), 
                overrideSaveDir = Path(overrideSaveDir).inSystem
            ) 
        }
        return TorrentSessionProxy(session, coroutineContext)
    }

    override fun getSaveDirForTorrent(data: PEncodedTorrentInfo?): String? {
        if (data == null) return null
        
        val path = delegate.getSaveDirForTorrent(data.toEncodedTorrentInfo())
        return path.absolutePath
    }

    override fun listSaves(): Array<String> {
        val saves = delegate.listSaves()
        
        return saves.map { it.absolutePath }.toTypedArray()
    }

    override fun close() {
        delegate.close()
    }
}