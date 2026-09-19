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
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.client.apis.OAuthAniApi
import me.him188.ani.client.models.AniUserAuthRoutingLoginResponse
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import kotlin.time.Duration.Companion.milliseconds


interface OAuthClient {
    /**
     * 获取 OAuth 注册链接, 通过此链接完成的 OAuth 授权将会自动注册一个新的 Ani 用户.
     *
     * @throws me.him188.ani.app.data.repository.RepositoryException
     * @throws IllegalArgumentException requestId 为空
     */
    suspend fun getOAuthRegisterLink(requestId: String): String

    /**
     * 获取 OAuth 绑定链接, 通过此链接完成的 OAuth 授权将会绑定到已有的 Ani 用户.
     *
     * @throws me.him188.ani.app.data.repository.RepositoryException
     * @throws IllegalArgumentException requestId 为空
     *
     */
    suspend fun getOAuthBindLink(requestId: String): String

    /**
     * 获取 OAuth 绑定或登录结果, 此结果将直接用于登录 ani 用户.
     *
     * @return null 表示还没有结果.
     * @throws me.him188.ani.app.data.repository.RepositoryRequestError 绑定模式下 (HTTP 409), 该第三方账号是另一个用户的唯一登录方式. 消息可以直接展示给用户.
     * @throws me.him188.ani.app.data.repository.RepositoryException
     * @throws IllegalArgumentException requestId 为空
     */
    suspend fun getResult(requestId: String): OAuthResult?
}

data class OAuthResult(
    val tokens: AccessTokenPair,
    val expiresInSeconds: Long,
    val refreshToken: String,
)

fun AniUserAuthRoutingLoginResponse.toOAuthResult(): OAuthResult {
    return OAuthResult(
        tokens = AccessTokenPair(
            aniAccessToken = this.tokens.accessToken,
            expiresAtMillis = this.tokens.expiresAtMillis,
            bangumiAccessToken = this.tokens.bangumiAccessToken,
        ),
        expiresInSeconds = this.tokens.expiresAtMillis.milliseconds.inWholeSeconds,
        refreshToken = this.tokens.refreshToken,
    )
}

class BangumiOAuthClient(
    private val bangumiApi: ApiInvoker<BangumiAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val platform: Platform = currentPlatform(),
) : OAuthClient {
    override suspend fun getOAuthRegisterLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }
        val resp = try {
            bangumiApi.invoke {
                oauth(requestId, platform.name.lowercase(), platform.arch.displayName.lowercase()).body()
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        return resp.url
    }

    override suspend fun getOAuthBindLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        val resp = try {
            bangumiApi.invoke {
                bind(requestId, platform.name.lowercase(), platform.arch.displayName.lowercase()).body()
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        return resp.url
    }

    override suspend fun getResult(requestId: String): OAuthResult? {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        try {
            val resp = bangumiApi.invoke {
                getToken(requestId).body()
            }

            return resp.toOAuthResult()
        } catch (ex: ClientRequestException) {
            when (ex.response.status) {
                HttpStatusCode.TooEarly -> return null
                HttpStatusCode.Conflict -> throw RepositoryRequestError(ex.response.bodyAsText(), cause = ex)
                else -> throw RepositoryException.wrapOrThrowCancellation(ex)
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

/**
 * 通过服务端的通用第三方登录接口 (`/users/oauth/{provider}/...`) 登录或绑定 [provider]. 除 Bangumi 外的平台都使用这个实现.
 */
class ExternalOAuthClient(
    private val provider: OAuthPlatform,
    private val oauthApi: ApiInvoker<OAuthAniApi>,
    private val platform: Platform = currentPlatform(),
) : OAuthClient {
    override suspend fun getOAuthRegisterLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }
        return try {
            oauthApi.invoke {
                startLogin(provider.id, requestId, platform.name.lowercase(), platform.arch.displayName.lowercase()).body()
            }.url
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun getOAuthBindLink(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }
        return try {
            oauthApi.invoke {
                startBind(provider.id, requestId, platform.name.lowercase(), platform.arch.displayName.lowercase()).body()
            }.url
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * @throws RepositoryRequestError 绑定模式下 (HTTP 409), 该第三方账号是另一个用户的唯一登录方式. 消息可以直接展示给用户.
     */
    override suspend fun getResult(requestId: String): OAuthResult? {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        try {
            return oauthApi.invoke { getResult(requestId).body() }.toOAuthResult()
        } catch (ex: ClientRequestException) {
            when (ex.response.status) {
                HttpStatusCode.TooEarly -> return null
                HttpStatusCode.Conflict -> throw RepositoryRequestError(ex.response.bodyAsText(), cause = ex)
                else -> throw RepositoryException.wrapOrThrowCancellation(ex)
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
