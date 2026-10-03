/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.client.apis.OAuthAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用 MockEngine 驱动真实的 [OAuthAniApi], 覆盖请求路径、参数与错误码到 [me.him188.ani.app.data.repository.RepositoryException] 的映射.
 */
class ExternalOAuthClientTest {
    private class Server(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()

        val api: ApiInvoker<OAuthAniApi> = object : ApiInvoker<OAuthAniApi> {
            private val engine = MockEngine { request ->
                requests += request
                handler(request)
            }

            // 与生产的 HttpClient 一致: 4xx/5xx 抛出 ClientRequestException / ServerResponseException
            private val client = OAuthAniApi(
                baseUrl = "http://test",
                httpClientEngine = engine,
                httpClientConfig = { it.expectSuccess = true },
            )

            override suspend fun <R> invoke(action: suspend OAuthAniApi.() -> R): R = action(client)
        }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private fun MockRequestHandleScope.text(body: String, status: HttpStatusCode): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "text/plain; charset=UTF-8"))

    private val loginResponse = """
        {"userId":"0e4b1e4a-6c1e-4f3f-9d0d-1b2c3d4e5f60",
         "tokens":{"accessToken":"at","refreshToken":"rt","expiresAtMillis":1000},
         "user":{"id":"0e4b1e4a-6c1e-4f3f-9d0d-1b2c3d4e5f60","nickname":"n","hasPassword":false,
                 "isBangumiSessionValid":false,"externalAccounts":[{"provider":"github","username":"octocat"}]}}
    """.trimIndent()

    @Test
    fun `register link uses the login route of the provider with request id and platform`() = runTest {
        val server = Server { json("""{"url":"https://github.com/login/oauth/authorize?state=s"}""") }
        val client = ExternalOAuthClient(OAuthPlatform.GITHUB, server.api)

        val url = client.getOAuthRegisterLink("req-1")

        assertEquals("https://github.com/login/oauth/authorize?state=s", url)
        val request = server.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/users/oauth/github/login"), request.url.toString())
        assertEquals("req-1", request.url.parameters["requestId"])
        assertTrue(!request.url.parameters["os"].isNullOrBlank())
        assertTrue(!request.url.parameters["arch"].isNullOrBlank())
    }

    @Test
    fun `bind link uses the bind route of the provider`() = runTest {
        val server = Server { json("""{"url":"https://github.com/login/oauth/authorize?state=b"}""") }

        val url = ExternalOAuthClient(OAuthPlatform.GITHUB, server.api).getOAuthBindLink("req-2")

        assertEquals("https://github.com/login/oauth/authorize?state=b", url)
        assertTrue(server.requests.single().url.encodedPath.endsWith("/users/oauth/github/bind"))
    }

    @Test
    fun `blank request id is rejected before any request`() = runTest {
        val server = Server { error("must not be called") }
        val client = ExternalOAuthClient(OAuthPlatform.GITHUB, server.api)
        assertFailsWith<IllegalArgumentException> { client.getOAuthRegisterLink(" ") }
        assertFailsWith<IllegalArgumentException> { client.getOAuthBindLink("") }
        assertFailsWith<IllegalArgumentException> { client.getResult("") }
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `result is null while the callback has not arrived`() = runTest {
        val server = Server { text("Not yet arrived or expired", HttpStatusCode.TooEarly) }

        assertNull(ExternalOAuthClient(OAuthPlatform.GITHUB, server.api).getResult("req-3"))
        val request = server.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/users/oauth/result"), request.url.toString())
        assertEquals("req-3", request.url.parameters["requestId"])
    }

    @Test
    fun `result carries the tokens`() = runTest {
        val server = Server { json(loginResponse) }

        val result = assertNotNull(ExternalOAuthClient(OAuthPlatform.GITHUB, server.api).getResult("req-4"))

        assertEquals("at", result.tokens.aniAccessToken)
        assertEquals("rt", result.refreshToken)
        assertNull(result.tokens.bangumiAccessToken)
    }

    @Test
    fun `conflict is surfaced with the message of the server for the user`() = runTest {
        val message = "这个 github 账号是另一个 Animeko 账号的唯一登录方式"
        val server = Server { text(message, HttpStatusCode.Conflict) }

        val e = assertFailsWith<RepositoryRequestError> {
            ExternalOAuthClient(OAuthPlatform.GITHUB, server.api).getResult("req-5")
        }
        assertEquals(message, e.localizedMessage)
    }

    @Test
    fun `server error is a service unavailable error`() = runTest {
        val server = Server { text("Failed to login to github", HttpStatusCode.ServiceUnavailable) }
        val e = assertFailsWith<Exception> {
            ExternalOAuthClient(OAuthPlatform.GITHUB, server.api).getResult("req-6")
        }
        assertIs<RepositoryServiceUnavailableException>(e)
    }
}
