/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import org.openani.anitorrent.binding.torrent_stats_t

class SwigTorrentStats(
    private val native: torrent_stats_t,
) : TorrentStats {
    override val total: Long
        get() = native.total
    override val totalDone: Long
        get() = native.total_done
    override val allTimeUpload: Long
        get() = native.all_time_upload
    override val allTimeDownload: Long
        get() = native.all_time_download
    override val downloadPayloadRate: Long
        get() = native.download_payload_rate.toUInt().toLong()
    override val uploadPayloadRate: Long
        get() = native.upload_payload_rate.toUInt().toLong()
    override val progress: Float
        get() = native.progress
    override val totalPayloadDownload: Long
        get() = native.total_payload_download
    override val totalPayloadUpload: Long
        get() = native.total_payload_upload
}
