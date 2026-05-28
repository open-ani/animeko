/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode.Companion.UnprocessableEntity
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.web.rewriteWebCorsProxyUrl
import me.him188.ani.client.apis.SubscriptionsAniApi
import me.him188.ani.utils.coroutines.withExceptionCollector
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlin.coroutines.cancellation.CancellationException

fun interface MediaSourceSubscriptionRequester {
    @Throws(RepositoryException::class, CancellationException::class)
    suspend fun request(
        subscription: MediaSourceSubscription,
    ): SubscriptionUpdateData
}

class MediaSourceSubscriptionRequesterImpl(
    private val client: ScopedHttpClient,
    private val api: ApiInvoker<SubscriptionsAniApi>,
) : MediaSourceSubscriptionRequester {
    /**
     * 执行网络请求, 下载新订阅数据.
     */
    @Throws(RepositoryException::class, CancellationException::class)
    override suspend fun request(
        subscription: MediaSourceSubscription,
    ): SubscriptionUpdateData {
        suspend fun HttpResponse.decode() = MediaSourceCodecManager.Companion.json.decodeFromString(
            SubscriptionUpdateData.serializer(),
            bodyAsText(),
        )

        withExceptionCollector {
            // 首先直连
            try {
                return client.use {
                    get(rewriteWebCorsProxyUrl(subscription.url)).decode()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                collect(e) // continue
            }

            // 失败则尝试代理服务
            return try {
                api {
                    proxy(subscription.url).response.decode()
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == UnprocessableEntity) {
                    throwLast() // ignore this exception, throw the previous one
                }
                throw e
            }
        }
    }
}
