/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.io.SystemPath
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * 下载管理器, 支持根据磁力链解析[种子信息][EncodedTorrentInfo], 然后根据种子信息创建下载会话 [TorrentSession].
 *
 * Must be closed when it is no longer needed.
 */
interface TorrentDownloader : AutoCloseable {
    /**
     * @see TorrentSession.Stats for comments
     */
    data class Stats(
        val totalSize: Long,
        /**
         * 所有文件的下载字节数之和.
         */
        val downloadedBytes: Long,
        /**
         * Bytes per second.
         */
        val downloadSpeed: Long,
        val uploadedBytes: Long,
        /**
         * Bytes per second.
         */
        val uploadSpeed: Long,
        /**
         * Bytes per second.
         */
        val downloadProgress: Float,
    )

    /**
     * 所有任务合计的统计信息.
     * @see TorrentSession.sessionStats
     */
    val totalStats: Flow<Stats>

    /**
     * Details about the underlying torrent library.
     */
    val vendor: TorrentLibInfo

    /**
     * Fetches a magnet link.
     *
     * @param uri supports magnet link or http link for the torrent file
     *
     * @throws FetchTorrentTimeoutException if timeout has been reached.
     */
    suspend fun fetchTorrent(uri: String, timeoutSeconds: Int = 60): EncodedTorrentInfo

    /**
     * Starts download of a torrent using the torrent data.
     *
     * This function may involve I/O operation e.g. to compare with local caches.
     *
     * @param overrideSaveDir 覆盖的保存目录. 注意, 若这是季度全集资源, 他可能会在 qBit 平台上出问题.
     * 因为 qBit 依赖保存目录获取已有资源.
     * 于是这个参数目前一直没使用.
     */
    suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
        overrideSaveDir: SystemPath? = null,
    ): TorrentSession

    fun getSaveDirForTorrent(
        data: EncodedTorrentInfo,
    ): SystemPath

    /**
     * 获取所有的种子保存目录列表
     */
    fun listSaves(): List<SystemPath>

    override fun close()
}

class FetchTorrentTimeoutException(
    override val message: String? = "Magnet fetch timeout",
    override val cause: Throwable? = null
) : Exception()

/**
 * 用于下载 `https://xxx.torrent`
 */
interface HttpFileDownloader : AutoCloseable {
    suspend fun download(url: String): ByteArray
}

class TorrentDownloaderConfig(
    val peerFingerprint: String = "-AL4000-",
    val userAgent: String = "ani_libtorrent/3.0.0", // "libtorrent/2.1.0.0", "ani_libtorrent/3.0.0"
    val handshakeClientVersion: String? = "3.0.0",
    /**
     * 0 means unlimited
     */
    val downloadRateLimitBytes: Int = 0,
    /**
     * 0 means unlimited
     */
    val uploadRateLimitBytes: Int = 0,
    /**
     * share ratio limit, 100 = 1.0
     */
    val shareRatioLimit: Int = 110,
)