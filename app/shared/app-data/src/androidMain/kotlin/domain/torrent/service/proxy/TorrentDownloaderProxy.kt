/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.Build
import android.os.DeadObjectException
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.callback.ITorrentDownloaderStatsCallback
import me.him188.ani.app.domain.torrent.client.ConnectivityAware
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderFetchTorrent
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderStartDownload
import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentDownloaderStats
import me.him188.ani.app.domain.torrent.parcel.PTorrentLibInfo
import me.him188.ani.app.domain.torrent.parcel.toParceled
import me.him188.ani.app.domain.torrent.parcel.toRemoteContinuationException
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.utils.coroutines.CancellationException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TorrentDownloaderProxy(
    private val delegate: TorrentDownloader,
    private val connectivityAware: ConnectivityAware,
    context: CoroutineContext,
) : IRemoteTorrentDownloader.Stub() {
    private val logger = logger<TorrentDownloaderProxy>()
    private val scope = context.childScope()

    override fun getTotalStatus(flow: ITorrentDownloaderStatsCallback?): IDisposableHandle {
        val job = scope.launch(Dispatchers.IO_) {
            delegate.totalStats.collect {
                if (!connectivityAware.isConnected) return@collect

                try {
                    flow?.onEmit(
                        PTorrentDownloaderStats(
                            it.totalSize,
                            it.downloadedBytes,
                            it.downloadSpeed,
                            it.uploadedBytes,
                            it.uploadSpeed,
                            it.downloadProgress,
                        ),
                    )
                } catch (doe: DeadObjectException) {
                    throw CancellationException("Cancelled collecting downloader total stats.", doe)
                }
            }
        }

        return DisposableHandleProxy { job.cancel() }
    }

    override fun getVendor(): PTorrentLibInfo {
        val vendor = delegate.vendor

        return PTorrentLibInfo(
            vendor.vendor,
            vendor.version,
            vendor.supportsStreaming,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun fetchTorrent(
        uri: String?,
        timeoutSeconds: Int,
        cont: ContTorrentDownloaderFetchTorrent?
    ): IDisposableHandle? {
        if (uri == null) return null
        if (cont == null) return null

        val job = scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (!connectivityAware.isConnected) return@CoroutineExceptionHandler
                cont.resumeWithException(throwable.toRemoteContinuationException())
            } + Dispatchers.IO_,
        ) {
            val result = delegate.fetchTorrent(uri, timeoutSeconds)
            if (!connectivityAware.isConnected) return@launch
            cont.resume(result.toParceled())
        }

        return DisposableHandleProxy { job.cancel() }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun startDownload(
        data: PEncodedTorrentInfo?,
        cont: ContTorrentDownloaderStartDownload?
    ): IDisposableHandle? {
        if (data == null) return null
        if (cont == null) return null

        val job = scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (!connectivityAware.isConnected) return@CoroutineExceptionHandler
                cont.resumeWithException(throwable.toRemoteContinuationException())
            } + Dispatchers.IO_,
        ) {
            val result = delegate.startDownload(withContext(Dispatchers.IO_) { data.toEncodedTorrentInfo() })
            if (!connectivityAware.isConnected) return@launch
            cont.resume(TorrentSessionProxy(result, connectivityAware, scope.coroutineContext))
        }

        return DisposableHandleProxy { job.cancel() }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
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