/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.him188.ani.client.apis.AnnouncementsAniApi
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.client.apis.NotificationsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 用 [MockEngine] 模拟通知、公告和功能开关接口. 与生产的 HTTP 客户端一样, 4xx/5xx 抛出 `ClientRequestException` / `ServerResponseException`.
 */
internal class NotificationTestServer(
    var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
) {
    val requests = mutableListOf<HttpRequestData>()

    private val engine = MockEngine { request ->
        requests += request
        handler(request)
    }

    private val notificationsApi = object : ApiInvoker<NotificationsAniApi> {
        private val client = NotificationsAniApi(
            baseUrl = "http://test",
            httpClientEngine = engine,
            httpClientConfig = { it.expectSuccess = true },
        )

        override suspend fun <R> invoke(action: suspend NotificationsAniApi.() -> R): R = action(client)
    }

    private val announcementsApi = object : ApiInvoker<AnnouncementsAniApi> {
        private val client = AnnouncementsAniApi(
            baseUrl = "http://test",
            httpClientEngine = engine,
            httpClientConfig = { it.expectSuccess = true },
        )

        override suspend fun <R> invoke(action: suspend AnnouncementsAniApi.() -> R): R = action(client)
    }

    private val featureFlagsApi = object : ApiInvoker<FeatureFlagsAniApi> {
        private val client = FeatureFlagsAniApi(
            baseUrl = "http://test",
            httpClientEngine = engine,
            httpClientConfig = { it.expectSuccess = true },
        )

        override suspend fun <R> invoke(action: suspend FeatureFlagsAniApi.() -> R): R = action(client)
    }

    val notificationService = AniNotificationService(notificationsApi, EmptyCoroutineContext)
    val announcementService = AniAnnouncementService(announcementsApi, EmptyCoroutineContext)
    val featureFlagService = AniFeatureFlagService(featureFlagsApi, EmptyCoroutineContext)
}

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf("Content-Type", "application/json"))

internal suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

internal suspend fun HttpRequestData.bodyJson(): JsonObject = Json.parseToJsonElement(bodyText()).jsonObject
