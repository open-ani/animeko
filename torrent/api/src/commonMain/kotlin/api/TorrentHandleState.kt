package me.him188.ani.app.torrent.api

/**
 * 引擎报告的任务状态, 对应 libtorrent 的 `torrent_status::state_t`, 顺序必须与其一致.
 */
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
