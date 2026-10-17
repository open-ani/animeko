package me.him188.ani.app.torrent.api

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.peer.PeerInfo

/**
 * 表示一整个 BT 资源的下载任务, 对应一个磁力链或 .torrent 文件.
 *
 * 一个种子资源可能包含任意数量 (含 0) 的文件 [TorrentFileEntry], 可通过 [getFiles] 获取.
 */
interface TorrentSession {
    data class Stats(
        /**
         * 所有已请求的文件的总大小.
         * 例如, 一个季度全集资源, 只请求下载前两集, 则该值为前两集的大小之和.
         */
        val totalSizeRequested: Long,
        /**
         * 已经下载成功的字节数.
         *
         * @return `0L`..[TorrentFileEntry.length]
         */
        val downloadedBytes: Long,
        /**
         * 当前下载速度, 字节每秒.
         */
        val downloadSpeed: Long,
        /**
         * 已经上传成功的字节数. 为持久化到 resume data 的值.
         *
         * @return `0L`..INF
         */
        val uploadedBytes: Long,
        /**
         * 当前上传速度, 字节每秒.
         */
        val uploadSpeed: Long,
        /**
         * Bytes per second.
         */
        val downloadProgress: Float,
    ) {
        val isDownloadFinished: Boolean get() = downloadProgress >= 1f

        // 注意, 因为这个 有 totalSize, 所以不提供 Zero 和 Unspecified
    }

    /**
     * 获取一个 flow, 用于监听综合统计信息的更新.
     *
     * 当该下载任务刚刚开始时, flow 会 emit null.
     *
     * 注意, 该 flow 的 emit 频率是未定义的. 它可能是每秒一次, 也可能每秒多次.
     */
    val sessionStats: Flow<Stats?>

    /**
     * 该 BT 资源的名称.
     *
     * 本函数将会一直挂起, 直到获取到信息.
     * 例如, 文件信息只有在成功解析磁力链后才会获取到.
     */
    suspend fun getName(): String

    /**
     * 获取该种子资源中的所有文件.
     *
     * 本函数将会一直挂起, 直到获取到信息.
     * 例如, 文件信息只有在成功解析磁力链后才会获取到.
     */
    suspend fun getFiles(): List<TorrentFileEntry>

    /**
     * 获取该 BT 任务的连接对象.
     */
    fun getPeers(): List<PeerInfo>

    /**
     * 引擎报告的任务状态. 任务已关闭或引擎不提供时为 `null`.
     */
    fun getState(): TorrentHandleState?

    /**
     * 关闭该下载任务, 释放资源. 将会等待 native 线程结束.
     */
    suspend fun close()

    /**
     * 仅当没有文件正在被使用时关闭该下载任务.
     */
    suspend fun closeIfNotInUse()
}
