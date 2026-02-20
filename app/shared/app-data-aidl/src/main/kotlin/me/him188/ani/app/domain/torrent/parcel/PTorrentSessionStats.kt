/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry

/**
 * Map to [TorrentDownloader.Stats].
 */
@Parcelize
class PTorrentSessionStats(
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
) : Parcelable {
    fun toStats(): TorrentSession.Stats {
        return TorrentSession.Stats(
            totalSizeRequested, 
            downloadedBytes, 
            downloadSpeed, 
            uploadedBytes, 
            uploadSpeed, 
            downloadProgress
        )
    }
}