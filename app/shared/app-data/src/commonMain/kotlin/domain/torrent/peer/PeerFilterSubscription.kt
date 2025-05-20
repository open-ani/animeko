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
import me.him188.ani.app.platform.currentAniBuildConfig

/**
 * Peer 过滤订阅信息
 */
@Serializable
data class PeerFilterSubscription(
    val subscriptionId: String,
    val url: String,
    val enabled: Boolean,
    val lastLoaded: LastLoaded?,
) {
    @Serializable
    data class RuleStat(
        val ipRuleCount: Int,
        val clientRuleCount: Int,
        val idRuleCount: Int,
    )

    @Serializable
    data class LastLoaded(
        val ruleStat: RuleStat?,
        val error: String?
    )

    companion object {
        const val BUILTIN_SUBSCRIPTION_ID = "ani.builtin.peerfilter.rule"
    }
}