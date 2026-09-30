/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

/**
 * 客户端支持的第三方登录平台. 服务端以 [id] 标识平台 (`GET /users/oauth/providers`), 服务端可能返回客户端不认识的平台, 此时 [fromId] 为 `null`.
 */
enum class OAuthPlatform(
    /**
     * 服务端使用的平台 ID
     */
    val id: String,
    val displayName: String,
) {
    BANGUMI("bangumi", "Bangumi"),
    GITHUB("github", "GitHub"),
    ;

    companion object {
        fun fromId(id: String): OAuthPlatform? = entries.firstOrNull { it.id == id }
    }
}
