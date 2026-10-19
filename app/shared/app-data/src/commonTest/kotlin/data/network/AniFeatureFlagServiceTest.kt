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
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.featureflag.FeatureFlagValue
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AniFeatureFlagServiceTest {
    private class Server(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        private val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        private val api = object : ApiInvoker<FeatureFlagsAniApi> {
            private val client = FeatureFlagsAniApi(
                baseUrl = "http://test",
                httpClientEngine = engine,
                httpClientConfig = { it.expectSuccess = true },
            )

            override suspend fun <R> invoke(action: suspend FeatureFlagsAniApi.() -> R): R = action(client)
        }
        val service = AniFeatureFlagService(api, EmptyCoroutineContext)
    }

    @Test
    fun `returns the flag values as sent by the server`() = runTest {
        val server = Server {
            respondJson("""{"flags":{"play_flow_analytics":{"enabled":true,"threshold":0.2},"future_flag":{"enabled":false},"on":{"enabled":true}}}""")
        }

        val flags = server.service.getFeatureFlags()

        assertEquals(
            mapOf(
                "play_flow_analytics" to FeatureFlagValue(enabled = true, threshold = 0.2f),
                "future_flag" to FeatureFlagValue.Off,
                "on" to FeatureFlagValue.On,
            ),
            flags,
        )
        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v2/feature-flags", request.url.encodedPath)
    }

    @Test
    fun `empty map when the server has no flags`() = runTest {
        val server = Server { respondJson("""{"flags":{}}""") }
        assertEquals(emptyMap(), server.service.getFeatureFlags())
    }

    @Test
    fun `server errors become repository exceptions`() = runTest {
        val server = Server { respond("", HttpStatusCode.InternalServerError) }
        assertFailsWith<RepositoryException> { server.service.getFeatureFlags() }
    }
}
