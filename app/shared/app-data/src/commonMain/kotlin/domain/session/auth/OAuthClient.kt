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
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.typeInfo
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.domain.session.AccessTokenPair
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

/**
 *
 */
interface OAuthClient {
    suspend fun getResult(requestId: String): OAuthResult?
    suspend fun refreshAccessToken(refreshToken: String): OAuthResult
    suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String
}

data class OAuthResult(
    val tokens: AccessTokenPair,
    val expiresInSeconds: Long,
    val refreshToken: String,
)

class OAuthClientImpl(
    private val oauthApiInvoker: ApiInvoker<BangumiOAuthAniApi>,
) : OAuthClient {
    override suspend fun getResult(requestId: String): OAuthResult? {
        return try {
            oauthApiInvoker {
                // TODO: 2025/4/8 未来我们需要在 Ani 服务器直接返回两个 token, 避免多次请求 
                val bangumiToken = getBangumiToken(requestId)
                    .typedBody<AniBangumiUserToken>(typeInfo<AniBangumiUserToken>())

                val aniToken = bangumiLogin(bangumiToken.accessToken).body().token

                OAuthResult(
                    tokens = AccessTokenPair(
                        aniAccessToken = aniToken,
                        expiresAtMillis = bangumiToken.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                        bangumiAccessToken = bangumiToken.accessToken,
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

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        return try {
            oauthApiInvoker {
                // TODO: 2025/4/8 未来我们需要在 Ani 服务器直接返回两个 token, 避免多次请求 
                val bangumiToken = refreshBangumiToken(AniRefreshBangumiTokenRequest(refreshToken)).body()
                val aniToken = bangumiLogin(bangumiToken.accessToken).body().token

                OAuthResult(
                    tokens = AccessTokenPair(
                        aniAccessToken = aniToken,
                        expiresAtMillis = bangumiToken.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                        bangumiAccessToken = bangumiToken.accessToken,
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
 * A [OAuthClient] that does nothing. Always get failure response [ApiFailure.ServiceUnavailable].
 */
object ConstantFailureOAuthClient : OAuthClient {
    override suspend fun getResult(requestId: String): OAuthResult? {
        throw RepositoryServiceUnavailableException()
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        throw RepositoryServiceUnavailableException()
    }

    override suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String {
        throw RepositoryServiceUnavailableException()
    }
}