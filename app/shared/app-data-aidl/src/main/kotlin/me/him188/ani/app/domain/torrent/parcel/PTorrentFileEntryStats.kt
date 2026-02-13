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
import me.him188.ani.app.torrent.api.files.TorrentFileEntry

@Parcelize
class PTorrentFileEntryStats(
    /**
     * 已经下载成功的字节数.
     *
     * @return `0L`..[TorrentFileEntry.length]
     */
    val downloadedBytes: Long,
    /**
     * 已完成比例.
     *
     * @return `0f`..`1f`, 在未开始下载时, 该值为 `0f`.
     */
    val downloadProgress: Float, // 0f..1f
) : Parcelable {
    fun toStats(): TorrentFileEntry.Stats {
        return TorrentFileEntry.Stats(downloadedBytes, downloadProgress)
    }
}