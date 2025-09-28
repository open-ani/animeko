/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import me.him188.ani.app.data.repository.RepositoryAuthorizationException

/**
 * 用于获取当前登录状态的接口.
 */
interface SessionStateProvider {
    /**
     * 当前的登录状态.
     *
     * 这个 flow 一定会 emit 一个值, 并且会在登录状态改变时发出新的值.
     *
     * 这个 flow 一定反映当前真实的登录状态, 也就是说它**不会**发射 [SessionState.Invalid] 作为初始值.
     * 在 APP 刚启动时, 因为我们可能需要刷新 token, [stateFlow] 可能不会立即 emit. 在刷新完成后, 它一定会 emit.
     */
    val stateFlow: Flow<SessionState>

    val eventFlow: Flow<SessionEvent>

    @Deprecated(
        "",
        ReplaceWith(
            "this.canAccessAniApiNow()",
            "me.him188.ani.app.domain.session.canAccessAniApiNow",
        ),
    )
    suspend fun isLoggedInNow() = canAccessBangumiApiNow()
}

/**
 * Ani 用户登录状态.
 */
sealed class SessionState {
    data class Invalid(
        val reason: InvalidSessionReason
    ) : SessionState()

    /**
     * 登录成功并且 token 还没有过期.
     */
    data class Valid(
        /**
         * 用户是否登录了 Bangumi. 如果登录了, 则可以使用 Bangumi 评论等功能.
         */
        val bangumiConnected: Boolean,
    ) : SessionState()
}

/**
 * 登录登出事件, 方便监听登录状态的变化.
 */
sealed interface SessionEvent {
    /**
     * 有一个新用户登录. 启动 APP 时的登录不会触发这个事件.
     */
    data object NewLogin : SessionEvent

//    /**
//     * 用户主动退出登录.
//     */
//    data object Logout : SessionEvent
}

/**
 * 登录状态无效的原因
 */
enum class InvalidSessionReason {
    /**
     * 没有保存的 token. 说明是新用户或者老用户退出了登录.
     */
    NO_TOKEN,
    NETWORK_ERROR,
    UNKNOWN,
}


/**
 * 判断当前的登录状态是否有效.
 */
suspend fun SessionStateProvider.canAccessAniApiNow(): Boolean {
    return when (stateFlow.first()) {
        is SessionState.Invalid -> false
        is SessionState.Valid -> true
    }
}

/**
 * 判断当前的登录状态是否有效, 并且能访问需要 Bangumi 授权的功能.
 */
suspend fun SessionStateProvider.canAccessBangumiApiNow(): Boolean {
    return when (val state = stateFlow.first()) {
        is SessionState.Invalid -> false
        is SessionState.Valid -> state.bangumiConnected
    }
}

suspend fun SessionStateProvider.checkAccessAniApiNow() {
    if (!canAccessAniApiNow()) {
        throw RepositoryAuthorizationException()
    }
}

suspend fun SessionStateProvider.checkAccessBangumiApiNow() {
    if (!canAccessBangumiApiNow()) {
        throw RepositoryAuthorizationException()
    }
}


fun <T> Flow<T>.restartOnNewLogin(sessionStateProvider: SessionStateProvider): Flow<T> =
    sessionStateProvider.stateFlow.flatMapLatest {
        this
    }
