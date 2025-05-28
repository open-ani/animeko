/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessBangumiApiNow
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import kotlin.time.Duration.Companion.milliseconds


interface OAuthClient {
    /**
     * 此 OAuth 方式是否支持注册 Ani 用户, 如果不支持, [getOAuthRegisterLink] 应该抛出 [NotSupportedForRegistration]
     */
    val supportRegistration: Boolean

    /**
     * 获取 OAuth 注册链接, 通过此链接完成的 OAuth 授权将会自动注册一个新的 Ani 用户.
     * 如果 [supportRegistration] 为 false, 则抛出 [NotSupportedForRegistration] 异常.
     *
     * @throws IllegalArgumentException requestId 为空
     */
    suspend fun getOAuthRegisterLink(requestId: String): String

    /**
     * 获取 OAuth 绑定链接, 通过此链接完成的 OAuth 授权将会绑定到已有的 Ani 用户.
     *
     * @throws IllegalStateException 当前 Ani 账户未登录或无效
     * @throws IllegalArgumentException requestId 为空
     *
     */
    suspend fun getOAuthBindLink(requestId: String): String

    /**
     * 获取 OAuth 绑定或登录结果, 此结果将直接用于登录 ani 用户.
     *
     * @return null 表示还没有结果.
     * @throws IllegalArgumentException requestId 为空
     * @throws OAuthException 服务端返回的 4xx 结果, 例如 bangumi oauth token 无效或 Ani 账号已绑定了一个 bangumi 账号.
     */
    suspend fun getResult(requestId: String): OAuthResult?
}

data class OAuthResult(
    val tokens: AccessTokenPair,
    val expiresInSeconds: Long,
    val refreshToken: String,
)

class NotSupportedForRegistration(override val message: String? = null) : IllegalStateException(message)

sealed class OAuthException : Exception()

/**
 * 当回调的 Bangumi token 无效时抛出此异常.
 */
class InvalidTokenException(override val message: String? = null) : OAuthException()

/**
 * 当登录的 Bangumi 账号已经绑定了其他的 Ani 账号时抛出此异常.
 */
class AlreadyBoundException(override val message: String? = null) : OAuthException()

class BangumiOAuthClient(
    private val bangumiApi: ApiInvoker<BangumiAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val platform: Platform = currentPlatform(),
) : OAuthClient {
    override val supportRegistration: Boolean = true

    override suspend fun getOAuthRegisterLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }
        val resp = bangumiApi.invoke {
            oauth(requestId, platform.name, platform.arch.displayName)
        }
        return resp.body().url
    }

    override suspend fun getOAuthBindLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        sessionStateProvider.checkAccessBangumiApiNow()
        check(sessionStateProvider.stateFlow.first() is SessionState.Valid)

        val resp = bangumiApi.invoke {
            bind(requestId, platform.name, platform.arch.displayName)
        }
        return resp.body().url
    }

    override suspend fun getResult(requestId: String): OAuthResult? {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        try {
            val resp = bangumiApi.invoke {
                getToken(requestId)
            }
            val result = resp.body()

            return OAuthResult(
                tokens = AccessTokenPair(
                    aniAccessToken = result.tokens.accessToken,
                    expiresAtMillis = result.tokens.expiresAtMillis,
                    bangumiAccessToken = result.tokens.bangumiAccessToken,
                ),
                expiresInSeconds = result.tokens.expiresAtMillis.milliseconds.inWholeSeconds,
                refreshToken = result.tokens.refreshToken,
            )
        } catch (ex: ClientRequestException) {
            when (ex.response.status) {
                HttpStatusCode.TooEarly -> return null
                HttpStatusCode.BadRequest -> throw InvalidTokenException(ex.response.bodyAsText())
                HttpStatusCode.Conflict -> throw AlreadyBoundException(ex.response.bodyAsText())
                else -> throw ex
            }
        }
    }
}