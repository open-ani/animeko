/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import me.him188.ani.app.torrent.anitorrent.HandleId
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.peer.PeerInfo
import org.openani.anitorrent.binding.PeerInfoList
import org.openani.anitorrent.binding.torrent_handle_t
import org.openani.anitorrent.binding.torrent_info_t

class SwigTorrentHandle(
    internal val native: torrent_handle_t,
) : TorrentHandle {
    override val id: HandleId get() = native.id
    override val isValid: Boolean get() = native.is_valid

    override fun postStatusUpdates() {
        native.post_status_updates()
    }

    override fun postSaveResume() {
        native.post_save_resume()
    }

    override fun resume() {
        native.resume()
    }

    override fun setFilePriority(index: Int, priority: FilePriority) {
        native.set_file_priority(index, priority.toLibtorrentValue())
    }

    override fun getState(): TorrentHandleState? {
        val state = native._state
        if (state == -1) {
            // 当关闭 session 时, 还会 set 文件优先级为 IGNORE, 导致 libtorrent 认为任务已经完成, 触发 [onTorrentFinished]. 
            // 但是此时 session 已经关闭, 调用 native get session 就会出错.
            // 示例日志:
            /*
2024-11-20 22:47:38,170 [DEBUG] AnitorrentDownloadSession: [5768824][TorrentDownloadControl] Prioritizing pieces: [5, 6, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42]
2024-11-20 22:47:40,486 [INFO ] EpisodePlayHistoryRepository: save or update play progress EpisodeHistory(episodeId=1353857, positionMillis=6355)
2024-11-20 22:47:40,488 [INFO ] VlcjVideoPlayerState: clearVideoSource: Cleaning up player
2024-11-20 22:47:40,488 [INFO ] AnitorrentEntry: [5768824] Close handle [Up to 21°C] Re：從零開始的異世界生活 第三季 - 08 (Baha 1920x1080 AVC AAC MP4) [CE8CEA36].mp4, remove priority request
2024-11-20 22:47:40,488 [INFO ] AnitorrentEntry: [5768824] Set file priority to IGNORE: [Up to 21°C] Re：從零開始的異世界生活 第三季 - 08 (Baha 1920x1080 AVC AAC MP4) [CE8CEA36].mp4
2024-11-20 22:47:40,488 [DEBUG] SeekableInputCallbackMedia: Closing CallbackMedia me.him188.ani.app.videoplayer.io.SeekableInputCallbackMedia@17ac9432
2024-11-20 22:47:40,489 [INFO ] AnitorrentDownloadSession: [5768824] closing
2024-11-20 22:47:41,028 [INFO ] AnitorrentDownloadSession: [5768824] onTorrentFinished
             */
            // 所以返回 null 表示 session 已经关闭
            return null
//            error("Failed to get state, native returned -1 (session is invalid)")
        }
        return TorrentHandleState.entries[state]
    }

    override fun reloadFile(): TorrentDescriptor {
        val res = native.reload_file()
        if (res != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) {
            throw IllegalStateException("Failed to reload file, native returned $res")
        }
        val info = native.get_info_view()
        return SwigTorrentDescriptor(
            info ?: throw IllegalStateException("Failed to get info view, native get_info_view returned null"),
        )
    }

    override fun getPeers(): List<PeerInfo> {
        val peerInfoList = PeerInfoList()
        native.get_peers(peerInfoList)
        return peerInfoList.map { SwigPeerInfo(it) }
    }

    override fun setPieceDeadline(index: Int, deadline: Int) {
        native.set_piece_deadline(index, deadline)
    }

    override fun clearPieceDeadlines() {
        native.clear_piece_deadlines()
    }

    override fun addTracker(tracker: String, tier: Short, failLimit: Short) {
        native.add_tracker(tracker, tier, failLimit)
    }

    override fun getMagnetUri(): String? = native.make_magnet_uri().takeIf { it.isNotBlank() }
}

class SwigTorrentDescriptor(
    private val native: torrent_info_t,
) : TorrentDescriptor {
    override val name: String
        get() = native.name
    override val fileCount: Int
        get() {
            val count = native.file_count()
            if (count > Int.MAX_VALUE.toLong()) {
                error("File count is too large to fit into Int: $count")
            }
            return count.toInt()
        }

    override fun fileAtOrNull(index: Int): TorrentFileInfo? = native.file_at(index)?.let(::SwigTorrentFileInfo)

    override val numPieces: Int
        get() = native.num_pieces
    override val lastPieceSize: Long
        get() = native.last_piece_size.toUInt().toLong()
    override val pieceLength: Long
        get() = native.piece_length.toUInt().toLong()
}

private fun FilePriority.toLibtorrentValue(): Short = when (this) {
    FilePriority.IGNORE -> 0
    FilePriority.LOW -> 1
    FilePriority.NORMAL -> 4
    FilePriority.HIGH -> 7
}