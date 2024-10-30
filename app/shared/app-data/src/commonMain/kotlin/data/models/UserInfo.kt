/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models

/**
 * 与数据源无关的用户信息
 */
data class UserInfo(
    val id: Int,
    /**
     * 对于自己, 一定有
     */
    val username: String?,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val sign: String? = null
) {
    companion object {
        val EMPTY = UserInfo(0, "")
    }
}