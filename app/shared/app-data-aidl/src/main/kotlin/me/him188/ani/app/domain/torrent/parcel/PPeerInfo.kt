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
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

/**
 * translate [totalDownload] and [totalUpload] to bytes
 */
@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
@Parcelize
class PPeerInfo(
    override val handle: Long,
    override val id: CharArray,
    override val client: String,
    override val ipAddr: String,
    override val ipPort: Int,
    override val progress: Float,
    val totalDownloadBytes: Long,
    val totalUploadBytes: Long,
    override val flags: Long
) : Parcelable, PeerInfo {
    @IgnoredOnParcel
    override val totalDownload: FileSize = totalDownloadBytes.bytes
    @IgnoredOnParcel
    override val totalUpload: FileSize = totalUploadBytes.bytes
}