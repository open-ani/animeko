/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.typeInfo
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.client.apis.BangumiOAuthAniApi
import me.him188.ani.client.infrastructure.HttpResponse
import me.him188.ani.client.models.AniBangumiLoginRequest
import me.him188.ani.client.models.AniBangumiLoginResponse
import me.him188.ani.client.models.AniBangumiUserToken
import me.him188.ani.client.models.AniRefreshBangumiTokenRequest
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.seconds

interface AniAuthClient {
    /**
     * 获取使用 Ani OAuth 登录的结果. 同时返回 bangumi 和 ani tokens.
     */
    suspend fun getResult(requestId: String): AniAuthResult?

    /**
     * 使用 refresh token 刷新得到新的 bangumi 和 ani tokens.
     */
    suspend fun refreshAccessToken(refreshToken: String): AniAuthResult

    /**
     * 用 [bangumiAccessToken] 登录 ani 账户, 返回 tokens.
     */
    suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String
}

data class AniAuthResult(
    val tokens: AccessTokenPair,
    val expiresInSeconds: Long,
    val refreshToken: String,
)

class AniAuthClientImpl(
    private val oauthApiInvoker: ApiInvoker<BangumiOAuthAniApi>,
) : AniAuthClient {
    override suspend fun getResult(requestId: String): AniAuthResult? {
        return try {
            oauthApiInvoker {
                // TODO: 2025/4/8 未来我们需要在 Ani 服务器直接返回两个 token, 避免多次请求 
                val bangumiToken = getBangumiToken(requestId)
                    .typedBody<AniBangumiUserToken>(typeInfo<AniBangumiUserToken>())

                val aniToken = bangumiLogin(bangumiToken.accessToken).body().token

                AniAuthResult(
                    tokens = AccessTokenPair(
                        bangumiAccessToken = bangumiToken.accessToken,
                        aniAccessToken = aniToken,
                        expiresAtMillis = bangumiToken.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                    ),
                    expiresInSeconds = bangumiToken.expiresIn,
                    refreshToken = bangumiToken.refreshToken,
//                    bangumiUserId = bangumiToken.userId,
                )
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return null
            }
            throw RepositoryException.wrapOrThrowCancellation(e)
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): AniAuthResult {
        return try {
            oauthApiInvoker {
                // TODO: 2025/4/8 未来我们需要在 Ani 服务器直接返回两个 token, 避免多次请求 
                val bangumiToken = refreshBangumiToken(AniRefreshBangumiTokenRequest(refreshToken)).body()
                val aniToken = bangumiLogin(bangumiToken.accessToken).body().token

                AniAuthResult(
                    tokens = AccessTokenPair(
                        bangumiAccessToken = bangumiToken.accessToken,
                        aniAccessToken = aniToken,
                        expiresAtMillis = bangumiToken.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                    ),
                    expiresInSeconds = bangumiToken.expiresIn,
                    refreshToken = bangumiToken.refreshToken,
                )
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String {
        try {
            return oauthApiInvoker {
                val aniToken = bangumiLogin(bangumiAccessToken).body().token
                aniToken
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend fun BangumiOAuthAniApi.bangumiLogin(bangumiAccessToken: String): HttpResponse<AniBangumiLoginResponse> =
        bangumiLogin(
            AniBangumiLoginRequest(
                bangumiAccessToken,
                clientArch = currentPlatform().arch.displayName,
                clientOS = currentPlatform().name,
                clientVersion = currentAniBuildConfig.versionName,
            ),
        )
}

/**
 * A [AniAuthClient] that does nothing. Always get failure response [ApiFailure.ServiceUnavailable].
 */
object ConstantFailureAniAuthClient : AniAuthClient {
    override suspend fun getResult(requestId: String): AniAuthResult? {
        throw RepositoryServiceUnavailableException()
    }

    override suspend fun refreshAccessToken(refreshToken: String): AniAuthResult {
        throw RepositoryServiceUnavailableException()
    }

    override suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String {
        throw RepositoryServiceUnavailableException()
    }
}