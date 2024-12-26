/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import me.him188.ani.app.torrent.anitorrent.HandleId
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.openani.anitorrent.binding.peer_info_t

class SwigPeerInfo(
    native: peer_info_t,
) : PeerInfo {
    override val handle: HandleId = native.torrent_handle_id
    override val id: CharArray = native.peer_id.toCharArray()
    override val client: String = native.client
    override val ipAddr: String = native.ip_addr
    override val ipPort: Int = native.ip_port
    override val progress: Float = native.progress
    override val totalDownload: FileSize = native.total_download.bytes
    override val totalUpload: FileSize = native.total_upload.bytes
    override val flags: Long = native.flags
}