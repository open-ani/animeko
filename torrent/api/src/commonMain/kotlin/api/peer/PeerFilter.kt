/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.peer

/**
 * A filter to reject peer connections of bit torrent.
 */
interface PeerFilter {
    /**
     * determine if this peer should be filtered out.
     * 
     * @return `true` if want to drop connection to the peer.
     */
    fun shouldBlock(info: PeerInfo): Boolean

    /**
     * Describe this rule
     */
    fun describe(): String
}