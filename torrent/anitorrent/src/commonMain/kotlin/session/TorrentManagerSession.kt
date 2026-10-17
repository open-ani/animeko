/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import kotlinx.io.files.Path
import me.him188.ani.app.torrent.anitorrent.HandleId
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.peer.PeerInfo

/**
 * libtorrent 的 session_t, 用来管理多个 torrent 任务
 */
interface TorrentManagerSession<Handle : TorrentHandle, AddInfo : TorrentAddInfo> {
    fun createTorrentHandle(): Handle
    fun createTorrentAddInfo(): AddInfo

    fun startDownload(handle: Handle, addInfo: AddInfo, saveDir: Path): Boolean
    fun releaseHandle(handle: Handle)

    fun resume()

    fun applyConfig(config: TorrentDownloaderConfig)
}

/**
 * Native handle
 */
interface TorrentHandle {
    val id: HandleId

    val isValid: Boolean

    fun postStatusUpdates()
    fun postSaveResume()

    fun resume()
    fun setFilePriority(index: Int, priority: FilePriority)

    /**
     * @return 当前状态, `null` if session is closed
     */
    fun getState(): TorrentHandleState?
    fun reloadFile(): TorrentDescriptor

    fun getPeers(): List<PeerInfo>

    fun setPieceDeadline(index: Int, deadline: Int)
    fun clearPieceDeadlines()

    fun addTracker(tracker: String, tier: Short = 0, failLimit: Short = 0)

    fun getMagnetUri(): String?
}


interface TorrentAddInfo {
    fun setMagnetUri(uri: String)
    fun setTorrentFilePath(absolutePath: String)

    fun setResumeDataPath(absolutePath: String)
}

