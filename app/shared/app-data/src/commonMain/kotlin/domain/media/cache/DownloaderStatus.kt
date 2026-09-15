/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.utils.httpdownloader.DownloadError
import me.him188.ani.utils.httpdownloader.DownloadStatus

/**
 * 下载引擎对一条记录的内部状态, 供详情页诊断下载为何停滞.
 */
sealed interface DownloaderStatus {
    /**
     * BT 引擎.
     */
    data class Torrent(
        /**
         * 引擎是否在运行. Android 上后台服务未连接时为 `false`, 此时其余字段没有意义.
         */
        val serviceConnected: Boolean,
        val startup: TorrentStartup,
        /**
         * 引擎报告的任务状态, 未知时为 `null`.
         */
        val state: TorrentHandleState?,
        /**
         * 已连接的节点数.
         */
        val connectedPeers: Int,
        /**
         * 已连接节点中拥有全部数据的节点数.
         */
        val seeds: Int,
    ) : DownloaderStatus

    /**
     * BT 任务的启动结果.
     */
    enum class TorrentStartup {
        /**
         * 已获取种子信息并选定了要下载的文件.
         */
        STARTED,

        /**
         * 启动阶段超时, 没有获取到种子信息; 记录显示为失败.
         */
        TIMED_OUT,

        /**
         * 种子中没有匹配剧集的视频文件.
         */
        NO_MATCHING_FILE,
    }

    /**
     * HTTP 引擎.
     */
    data class Http(
        val status: DownloadStatus,
        val error: DownloadError?,
        val downloadedSegments: Int,
        val totalSegments: Int,
    ) : DownloaderStatus
}
