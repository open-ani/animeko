/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.torrent.anitorrent.session.SwigPeerInfo
import me.him188.ani.app.torrent.anitorrent.session.SwigTorrentAddInfo
import me.him188.ani.app.torrent.anitorrent.session.SwigTorrentHandle
import me.him188.ani.app.torrent.anitorrent.session.SwigTorrentManagerSession
import me.him188.ani.app.torrent.anitorrent.session.SwigTorrentResumeData
import me.him188.ani.app.torrent.anitorrent.session.SwigTorrentStats
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.logging.info
import org.openani.anitorrent.binding.event_listener_t
import org.openani.anitorrent.binding.new_event_listener_t
import org.openani.anitorrent.binding.peer_filter_t
import org.openani.anitorrent.binding.peer_info_t
import org.openani.anitorrent.binding.session_settings_t
import org.openani.anitorrent.binding.session_t
import org.openani.anitorrent.binding.torrent_resume_data_t
import org.openani.anitorrent.binding.torrent_state_t
import org.openani.anitorrent.binding.torrent_stats_t
import kotlin.coroutines.CoroutineContext

internal fun TorrentDownloaderConfig.toSessionSettings(): session_settings_t {
    val config = this
    return session_settings_t().apply {
        user_agent = config.userAgent
        peer_fingerprint = config.peerFingerprint
        handshake_client_version = config.handshakeClientVersion
        download_rate_limit = config.downloadRateLimitBytes
        upload_rate_limit = config.uploadRateLimitBytes
        share_ratio_limit = config.shareRatioLimit
        listOf(
            "router.utorrent.com:6881",
            "router.bittorrent.com:6881",
            "dht.transmissionbt.com:6881",
            "router.bitcomet.com:6881",
        ).forEach {
            dht_bootstrap_nodes_extra_add(it)
        }
    }

}

internal actual fun createAnitorrentTorrentDownloader(
    rootDataDirectory: SystemPath,
    httpFileDownloader: HttpFileDownloader,
    torrentDownloaderConfig: TorrentDownloaderConfig,
    parentCoroutineContext: CoroutineContext
): AnitorrentTorrentDownloader<*, *> {
    AnitorrentLibraryLoader.loadLibraries()
    AnitorrentTorrentDownloader.logger.info { "Creating a new AnitorrentTorrentDownloader" }

    val session = session_t()
    val settings = torrentDownloaderConfig.toSessionSettings()
    try {
        session.start(settings)
    } finally {
        settings.delete() // 其实也可以等 GC, 不过反正我们都不用了
    }
    AnitorrentTorrentDownloader.logger.info { "AnitorrentTorrentDownloader created" }
    return SwigAnitorrentTorrentDownloader(
        rootDataDirectory = rootDataDirectory,
        native = SwigTorrentManagerSession(session),
        httpFileDownloader = httpFileDownloader,
        parentCoroutineContext = parentCoroutineContext,
    )
}


internal class SwigAnitorrentTorrentDownloader(
    rootDataDirectory: SystemPath,
    override val native: SwigTorrentManagerSession,
    httpFileDownloader: HttpFileDownloader,
    parentCoroutineContext: CoroutineContext
) : AnitorrentTorrentDownloader<SwigTorrentHandle, SwigTorrentAddInfo>(
    rootDataDirectory,
    httpFileDownloader,
    parentCoroutineContext,
) {
    // native peer filter is always created
    private val nativePeerFilter = object : peer_filter_t() {
        override fun on_filter(info: peer_info_t?): Boolean {
            if (info == null) return false
            val filter = filter ?: return false
            return filter.shouldBlock(SwigPeerInfo(info))
        }
    }

    private val eventListener = object : event_listener_t() {
        override fun on_save_resume_data(handleId: Long, data: torrent_resume_data_t?) {
            data ?: return
            dispatchToSession(handleId) {
                it.onSaveResumeData(SwigTorrentResumeData(data))
            }
        }

        /*
2024-08-28 19:44:22,030 [INFO ] AnitorrentTorrentDownloader: [14167745] AnitorrentDownloadSession created, adding 29 trackers
2024-08-28 19:44:22,030 [INFO ] AnitorrentTorrentDownloader: withHandleTaskQueue: executed 1 delayed tasks
2024-08-28 19:44:23,307 [INFO ] AnitorrentDownloadSession: [14167745] onMetadataReceived
2024-08-28 19:44:23,309 [INFO ] AnitorrentDownloadSession: [14167745] onTorrentFinished
2024-08-28 19:44:23,309 [INFO ] AnitorrentDownloadSession: [14167745] onTorrentFinished
2024-08-28 19:44:23,310 [INFO ] AnitorrentDownloadSession: [14167745] onTorrentChecked
2024-08-28 19:44:23,310 [INFO ] AnitorrentDownloadSession: [14167745] reloadFiles
2024-08-28 19:44:23,311 [INFO ] AnitorrentDownloadSession: initializeTorrentInfo
2024-08-28 19:44:23,311 [INFO ] AnitorrentDownloadSession: [14167745] File '[ANi] 深夜 Punch - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4' piece initialized, 778 pieces, index range: 0..777, offset range: Piece(0..524287)..Piece(407371776..407818747)
2024-08-28 19:44:23,311 [INFO ] AnitorrentDownloadSession: [14167745] Got torrent info: TorrentInfo(name=[ANi] 深夜 Punch - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4, numPieces=778, entries.size=1)
2024-08-28 19:44:23,312 [INFO ] AnitorrentDownloadSession: [14167745] saving resume data to: /Users/him188/Library/Caches/me.Him188.Ani-debug/torrent-data2/anitorrent/pieces/917960765/fastresume
2024-08-28 19:44:23,313 [INFO ] TorrentVideoSource: TorrentVideoSource selected file: [ANi] 深夜 Punch - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4
2024-08-28 19:44:23,313 [INFO ] AnitorrentEntry: [14167745] Set file priority to HIGH: [ANi] 深夜 Punch - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4
         */

        // 新旧都会触发这个, 见上面的 log, 但似乎有时候会收不到这个事件
        override fun on_checked(handleId: Long) {
            dispatchToSession(handleId) {
                it.onTorrentChecked()
            }
        }

        // 看起来只有新资源才会触发这个, 见上面的 log
        override fun on_metadata_received(handleId: Long) {
            dispatchToSession(handleId) {
                it.onMetadataReceived()
            }
        }

        override fun on_block_downloading(handleId: Long, pieceIndex: Int, blockIndex: Int) {
            dispatchToSession(handleId) {
                it.onPieceDownloading(pieceIndex)
            }
        }

        override fun on_piece_finished(handleId: Long, pieceIndex: Int) {
            dispatchToSession(handleId) {
                it.onPieceFinished(pieceIndex)
            }
        }

        override fun on_torrent_state_changed(handleId: Long, state: torrent_state_t?) {
            state ?: return
            dispatchToSession(handleId) {
                if (state == torrent_state_t.finished) {
                    // 注意, 这可能会调用多次
                    it.onTorrentFinished()
                }
            }
        }

        override fun on_status_update(handleId: Long, stats: torrent_stats_t?) {
            stats ?: return
            dispatchToSession(handleId) {
                it.onStatsUpdate(SwigTorrentStats(stats))
            }
        }

        override fun on_file_completed(handleId: Long, fileIndex: Int) {
            dispatchToSession(handleId) {
                it.onFileCompleted(fileIndex)
            }
        }

        override fun on_torrent_removed(handleId: Long, torrentName: String) {
            if (handleId != 0L) {
                dispatchToSession(handleId) {
                    it.onTorrentRemoved()
                }
            } else {
                // torrent_removed_alerts 事件处理时其 handle 可能已经无效
                // 但是它的 torrent_name 还是有效的
                // 在这里 runBlocking 是没问题的, 因为 on_torrent_removed 一定只会在 actualTorrentInfo 之后调用
                runBlocking(Dispatchers.IO) {
                    openSessions.value.values.firstOrNull { session ->
                        session.getName() == torrentName
                    }
                }?.onTorrentRemoved()
            }
        }
    }

    private val eventSignal = Channel<Unit>(1)

    // must keep referenced
    private val newEventListener = object : new_event_listener_t() {
        override fun on_new_events() {
            // 根据 libtorrent 文档, 这里面不能处理事件
            eventSignal.trySend(Unit)
        }
    }

    init {
        native.native.set_new_event_listener(newEventListener)
        native.native.set_peer_filter(nativePeerFilter)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                eventSignal.receive() // await new events
                native.native.process_events(eventListener) // can block thread
            }
        }
    }

    override fun close() {
        native.native.remove_listener() // must remove, before gc-ing this object
        native.native.set_peer_filter(null) // clear filter
        super.close()
    }
}
