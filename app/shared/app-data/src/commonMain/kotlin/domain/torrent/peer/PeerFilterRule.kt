/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.peer

import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.client.models.AniPeerFilterRule

/**
 * 一个 Peer 过滤规则, 此规则可以从[订阅][PeerFilterSubscriptionRepository]或[本地 preference][TorrentPeerConfig] 构建.
 */
@Serializable
data class PeerFilterRule(
    /**
     * IP 过滤的样式, 用于过滤 [PeerInfo.ipAddr]
     *
     * 支持以下格式：
     * * 无类别域间路由（CIDR）
     *   例如：`10.0.0.1/24` 将过滤从 `10.0.0.0` 至 `10.0.0.255` 的所有 IP
     *   `ff06:1234::/64` 将过滤从 `ff06:1234::` 至 `ff06:1234::ffff:ffff:ffff:ffff` 的所有 IP
     * * 通配符
     *   例如：`10.0.12.*` 将过滤从 `10.0.12.0` 至 `10.0.12.255` 的所有 IP
     *   `ff06:1234::*` 将过滤从 `ff06:1234::` 至 `ff06:1234::ffff` 的所有 IP
     *   支持多级通配符，例如 `10.0.*.*`
     * * 范围表示
     *   例如 `10.0.24.100-200` 和 `ff06:1234::cafe-dead`
     */
    val blockedIpPattern: List<String>,

    /**
     * Peer ID 过滤的正则表达式, 用于过滤 [PeerInfo.id]
     */
    val blockedIdRegex: List<String>,

    /**
     * Peer 客户端名称过滤的正则表达式, 用于过滤 [PeerInfo.client]
     */
    val blockedClientRegex: List<String>
) {
    companion object {
        val Empty = PeerFilterRule(emptyList(), emptyList(), emptyList())
    }
}

fun AniPeerFilterRule.toPeerFilterRule(): PeerFilterRule {
    return PeerFilterRule(
        blockedIpPattern = blockedIpPattern,
        blockedIdRegex = blockedIdRegex,
        blockedClientRegex = blockedClientRegex,
    )
}