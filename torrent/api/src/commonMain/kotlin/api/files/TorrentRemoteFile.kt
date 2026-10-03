/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.files

/**
 * A file entry whose engine streams it from far away until the whole file is on disk.
 *
 * Seek-bar frame previews are refused for such a file while [isStreamingRemotely]: a player's
 * preview decodes the full-size stream a network round trip per frame, which never keeps up with
 * a drag and competes with playback for the same connections.
 */
interface TorrentRemoteFile {
    val isStreamingRemotely: Boolean
}
