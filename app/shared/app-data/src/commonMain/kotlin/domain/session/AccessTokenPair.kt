/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * 捆绑 Bangumi 和 Ani 的 accessTokens, 简化授权逻辑
 */
data class AccessTokenPair(
    val aniAccessToken: String,
    val expiresAtMillis: Long,
    val bangumiAccessToken: String?,
) {
    override fun toString(): String {
        // 日志不打印 token
        return "AccessTokenPair(bangumiAccessToken.hashCode=${bangumiAccessToken.hashCode()}, aniAccessToken.hashCode=${aniAccessToken.hashCode()}, expiresAtMillis=$expiresAtMillis)"
    }
}

fun AccessTokenPair.isExpired(clock: Clock = Clock.System): Boolean {
    return expiresAtMillis <=
            (clock.now().toEpochMilliseconds() + 1.hours.inWholeMilliseconds) // 提前 1 小时让 token 过期, 避免交给服务器时 403.
}
