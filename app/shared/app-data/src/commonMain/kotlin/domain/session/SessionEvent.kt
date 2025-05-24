/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

sealed interface SessionEvent {
    sealed interface UserChanged : SessionEvent

    /**
     * 有一个新用户登录. 启动 APP 时的登录不会触发这个事件.
     */
    data object NewLogin : UserChanged

    /**
     * 用户主动退出登录, 或者我们发现 token 过期了.
     */
    data object Logout : UserChanged
}
