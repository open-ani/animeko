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

// v2::torrent_status::state_t
enum class TorrentHandleState {
    QUEUED_FOR_CHECKING,
    CHECKING_FILES,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    ALLOCATING,
    CHECKING_RESUME_DATA
    /*
    		// the different overall states a torrent can be in
		enum state_t
		{
#if TORRENT_ABI_VERSION == 1
			// The torrent is in the queue for being checked. But there
			// currently is another torrent that are being checked.
			// This torrent will wait for its turn.
			queued_for_checking TORRENT_DEPRECATED_ENUM,
#else
			// internal
			unused_enum_for_backwards_compatibility,
#endif

			// The torrent has not started its download yet, and is
			// currently checking existing files.
			checking_files,

			// The torrent is trying to download metadata from peers.
			// This implies the ut_metadata extension is in use.
			downloading_metadata,

			// The torrent is being downloaded. This is the state
			// most torrents will be in most of the time. The progress
			// meter will tell how much of the files that has been
			// downloaded.
			downloading,

			// In this state the torrent has finished downloading but
			// still doesn't have the entire torrent. i.e. some pieces
			// are filtered and won't get downloaded.
			finished,

			// In this state the torrent has finished downloading and
			// is a pure seeder.
			seeding,

			// If the torrent was started in full allocation mode, this
			// indicates that the (disk) storage for the torrent is
			// allocated.
#if TORRENT_ABI_VERSION == 1
			allocating TORRENT_DEPRECATED_ENUM,
#else
			unused_enum_for_backwards_compatibility_allocating,
#endif

			// The torrent is currently checking the fast resume data and
			// comparing it to the files on disk. This is typically
			// completed in a fraction of a second, but if you add a
			// large number of torrents at once, they will queue up.
			checking_resume_data
		};

     */
}

interface TorrentAddInfo {
    fun setMagnetUri(uri: String)
    fun setTorrentFilePath(absolutePath: String)

    fun setResumeDataPath(absolutePath: String)
}

