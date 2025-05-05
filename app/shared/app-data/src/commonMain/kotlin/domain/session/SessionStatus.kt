/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.domain.session.SessionStatus.Verified
import kotlin.contracts.contract

/**
 * 表示登录会话的状态.
 *
 * 状态主要分为这几类:
 * - [Guest]: 没有保存的 access token, 视为游客模式.
 * - [Verifying]: 正在进行初次登录, 或者正在使用 refresh token 更换新 access token, 或者正在验证 access token 的有效性.
 * - [Refreshing]: 正在使用 refresh token 更换新 access token
 * - [Verified]: 登录完全成功. 这意味着已经连接到服务器测试过 token 是有效的.
 * - [VerificationFailed]: 有 token, 但是验证失败, 细分为一些详细原因.
 */
sealed interface SessionStatus {
    /**
     * 一个拥有 access token 的状态, 但这个 token 是未经过验证的.
     */
    sealed interface HasAccessToken : SessionStatus {
        val accessTokenMaybeUnverified: AccessTokenPair
    }

    /**
     * 表示正在加载中的中间状态. [Refreshing] 或者 [Verifying].
     */
    sealed class Loading : SessionStatus

    /**
     * 完成的状态
     */
    sealed class Final : SessionStatus

    /**
     * 正在使用 refresh token 换取新的 access token
     */
    data object Refreshing : SessionStatus, Loading()

    /**
     * 正在进行初次登录, 或者正在验证 access token 的有效性.
     */
    data class Verifying(
        override val accessTokenMaybeUnverified: AccessTokenPair,
    ) : HasAccessToken, Loading()

    /**
     * 登录完全成功. 这意味着已经连接到服务器测试过 token 是有效的.
     */
    data class Verified(
        override val accessTokenMaybeUnverified: AccessTokenPair,
        val userInfo: UserInfo,
    ) : SessionStatus, HasAccessToken, Final() {
        val accessToken get() = accessTokenMaybeUnverified
    }

    /**
     * 用户希望以游客身份登录
     */
    data object Guest : SessionStatus, Final()

    /**
     * 有 token, 但是验证失败
     */
    sealed class VerificationFailed : SessionStatus, Final()

    /**
     * 有 token, 但是验证失败, 因为 token 过期了.
     *
     * 仅当同时没有 refresh token, 或者使用 refresh token 也失败了才会有此状态.
     */
    data class Expired(
        val cause: Throwable? = null, // for debug
    ) : VerificationFailed()

    /**
     * 有 token, 但是验证失败, 因为网络问题
     */
    data object NetworkError : VerificationFailed()

    /**
     * 有 token, 但是验证失败, 因为服务器炸了
     */
    data object ServiceUnavailable : VerificationFailed()

    /**
     * 有 token, 但是验证失败, 因为意料之外的错误
     */
    data class UnknownError(
        val exception: Throwable,
    ) : VerificationFailed()
}

/**
 * 捆绑 Bangumi 和 Ani 的 accessTokens, 简化授权逻辑
 */
data class AccessTokenPair(
    val bangumiAccessToken: String,
    val aniAccessToken: String,
    val expiresAtMillis: Long,
) {
    override fun toString(): String {
        // 日志不打印 token
        return "AccessTokenPair(bangumiAccessToken.hashCode=${bangumiAccessToken.hashCode()}, aniAccessToken.hashCode=${aniAccessToken.hashCode()}, expiresAtMillis=$expiresAtMillis)"
    }
}

/**
 * 获取未经验证的 access token. 未经验证, 也就是说这个 token 可能是:
 * - 是有效的
 * - 已经过期
 * - 没过期, 但是在服务器上已经无效了 (比如因为用户更改了密码)
 */
val SessionStatus.unverifiedAccessTokenOrNull: AccessTokenPair? get() = (this as? SessionStatus.HasAccessToken)?.accessTokenMaybeUnverified

/**
 * 获取当前经过验证的用户信息. 如果用户未登录, 则返回 `null`.
 */
val SessionStatus.userInfoOrNull: UserInfo? get() = (this as? Verified)?.userInfo

/**
 * 获取当前经过验证的登录用户的用户名. 如果用户未登录, 则返回 `null`.
 */
val SessionStatus.usernameOrNull: String? get() = userInfoOrNull?.username

fun SessionStatus.isFinal(): Boolean {
    contract {
        returns(true) implies (this@isFinal is SessionStatus.Final)
    }
    return this is SessionStatus.Final
}
