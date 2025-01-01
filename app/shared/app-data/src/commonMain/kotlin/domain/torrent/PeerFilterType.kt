/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import me.him188.ani.app.torrent.api.peer.PeerFilter
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.utils.ipparser.IpSeqRange

class PeerIpFilter(private val pattern: String) : PeerFilter {
    private val parser = IpSeqRange.parse(pattern)
    override fun shouldBlock(info: PeerInfo): Boolean {
        return parser.contains(info.ipAddr)
    }

    override fun describe(): String {
        return "PeerIpFilter(${pattern})"
    }
}

class PeerClientFilter(patternRegex: String) : PeerFilter {
    private val regex = Regex(patternRegex)
    override fun shouldBlock(info: PeerInfo): Boolean {
        return info.client.contains(regex)
    }

    override fun describe(): String {
        return "PeerClientFilter(${regex.pattern})"
    }
}

object PeerInvalidIdFilter : PeerFilter {
    private const val FINGERPRINT_TRIM = '-'

    override fun shouldBlock(info: PeerInfo): Boolean {
        val idArr = info.id
        // id 开头不是 fingerprint 就拒绝连接 
        if (idArr[0] != FINGERPRINT_TRIM) return true
        if (idArr.lastIndexOf(FINGERPRINT_TRIM) == 0) return true
        return false
    }

    override fun describe(): String {
        return "PeerInvalidIdFilter"
    }
}

class PeerIdFilter(patternRegex: String) : PeerFilter {
    private val regex = Regex(patternRegex)
    override fun shouldBlock(info: PeerInfo): Boolean {
        val decoded = info.id.joinToString("")
        return decoded.contains(regex)
    }

    override fun describe(): String {
        return "PeerIdFilter(${regex.pattern})"
    }
}

class PeerIpBlackListFilter(private val ipList: List<String>) : PeerFilter {
    override fun shouldBlock(info: PeerInfo): Boolean {
        return ipList.contains(info.ipAddr)
    }

    override fun describe(): String = "PeerIpBlackListFilter"
}