/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.domain.torrent.peer.PeerFilterRule
import me.him188.ani.app.torrent.api.peer.PeerInfo

@Serializable
data class TorrentPeerConfig(
    // region: peer filter
    val enableIpFilter: Boolean = false,
    val enableClientFilter: Boolean = false,
    val enableIdFilter: Boolean = false,

    val ipBlackList: List<String> = emptyList(),
    /**
     * 本地手动编辑的规则
     */
    val localRule: PeerFilterRule = PeerFilterRule.Empty,

    /**
     * [PeerInfo.id] 一般是以 ASCII `-xxxxxx-` 开头的 ByteArray. 例如 Ani 的是类似 `-AL4000-`.
     * 如果 Peer 连接的客户端不是以此开头, 可以将其视为异常客户端.
     */
    val blockInvalidId: Boolean = true,
    // end region: peer filter

    @Suppress("PropertyName")
    @Transient val _placeholder: Int = 0,
) {
    /**
     * 创建 [PeerFilterRule], 只返回启用的规则
     */
    fun createRuleWithEnabled(): PeerFilterRule {
        return PeerFilterRule(
            blockedIpPattern = (if (enableIpFilter) localRule.blockedIpPattern else emptyList()) + ipBlackList,
            blockedIdRegex = if (enableIdFilter) localRule.blockedIdRegex else emptyList(),
            blockedClientRegex = if (enableClientFilter) localRule.blockedClientRegex else emptyList(),
        )
    }
    
    companion object {
        val Default = TorrentPeerConfig()
    }
}