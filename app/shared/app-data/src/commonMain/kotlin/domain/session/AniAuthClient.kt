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
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.client.apis.BangumiOAuthAniApi
import me.him188.ani.client.models.AniAnonymousBangumiUserToken
import me.him188.ani.client.models.AniBangumiUserToken
import me.him188.ani.client.models.AniRefreshBangumiTokenRequest
import me.him188.ani.utils.ktor.ApiInvoker

interface AniAuthClient {
    suspend fun getResult(requestId: String): ApiResponse<AniBangumiUserToken?>
    suspend fun refreshAccessToken(refreshToken: String): ApiResponse<AniAnonymousBangumiUserToken>
}

class AniAuthClientImpl(
    private val oauthApiInvoker: ApiInvoker<BangumiOAuthAniApi>,
) : AniAuthClient {
    override suspend fun getResult(requestId: String) = runApiRequest {
        try {
            oauthApiInvoker {
                getBangumiToken(requestId)
                    .typedBody<AniBangumiUserToken>(typeInfo<AniBangumiUserToken>())
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@runApiRequest null
            }
            throw e
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String) = runApiRequest {
        oauthApiInvoker { refreshBangumiToken(AniRefreshBangumiTokenRequest(refreshToken)).body() }
    }
}

/**
 * A [AniAuthClient] that does nothing. Always get failure response [ApiFailure.ServiceUnavailable].
 */
object ConstantFailureAniAuthClient : AniAuthClient {
    override suspend fun getResult(requestId: String): ApiResponse<AniBangumiUserToken?> {
        return ApiResponse.failure(ApiFailure.ServiceUnavailable)
    }

    override suspend fun refreshAccessToken(refreshToken: String): ApiResponse<AniAnonymousBangumiUserToken> {
        return ApiResponse.failure(ApiFailure.ServiceUnavailable)
    }
}