/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.client.apis.QRLoginAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class QrLoginRepositoryTest {
    private class Server(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()

        val api: ApiInvoker<QRLoginAniApi> = object : ApiInvoker<QRLoginAniApi> {
            private val engine = MockEngine { request ->
                requests += request
                handler(request)
            }

            // 与生产的 HttpClient 一致: 4xx/5xx 抛出 ClientRequestException / ServerResponseException
            private val client = QRLoginAniApi(
                baseUrl = "http://test",
                httpClientEngine = engine,
                httpClientConfig = { it.expectSuccess = true },
            )

            override suspend fun <R> invoke(action: suspend QRLoginAniApi.() -> R): R = action(client)
        }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private val tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial))

    private fun TestScope.repository(server: Server): QrLoginRepository =
        DefaultQrLoginRepository(server.api, createSessionManager(backgroundScope))

    private fun createSessionManager(scope: CoroutineScope) = SessionManager(
        tokenRepository = tokenRepository,
        coroutineScope = scope,
        refreshSession = { error("refresh should not be called") },
    )

    private val session = QrLoginSession("request-1", "secret-1", "http://test/qr", lifetime = 5.minutes)

    @Test
    fun `createSession sends the device name`() = runTest {
        val server = Server {
            json("""{"requestId":"r","pollSecret":"s","qrContent":"http://test/static/immutable/qrLogin.v1.html?requestId=r","expiresAtMillis":42,"expiresInSeconds":300}""")
        }
        val created = repository(server).createSession("Living room TV")

        assertEquals("r", created.requestId)
        assertEquals("s", created.pollSecret)
        assertEquals(5.minutes, created.lifetime)
        assertEquals("r", QrLoginRepository.parseRequestId(created.qrContent))
        val request = server.requests.single()
        assertEquals("/v2/users/qr-login", request.url.encodedPath)
        assertTrue("Living room TV" in request.body.toByteArray().decodeToString())
    }

    @Test
    fun `poll maps pending states and sends the secret in the body`() = runTest {
        var body = """{"status":"PENDING"}"""
        val server = Server { json(body) }
        val repository = repository(server)

        assertEquals(QrLoginPollResult.Pending, repository.poll(session))
        body = """{"status":"SCANNED","scannedByNickname":"小明"}"""
        assertEquals(QrLoginPollResult.Scanned("小明"), repository.poll(session))
        body = """{"status":"REJECTED"}"""
        assertEquals(QrLoginPollResult.Rejected, repository.poll(session))

        val request = server.requests.first()
        assertFalse("secret-1" in request.url.toString())
        assertTrue("secret-1" in request.body.toByteArray().decodeToString())
        assertEquals(GuestSession, tokenRepository.session.first())
    }

    @Test
    fun `poll approved saves the session`() = runTest {
        val server = Server {
            json(
                """
                {"status":"APPROVED","login":{"userId":"u","tokens":{"accessToken":"access","refreshToken":"refresh",
                "expiresAtMillis":99,"bangumiAccessToken":null},"user":{"id":"u","nickname":"n","registerTime":0,
                "lastLoginTime":0,"hasPassword":false,"isBangumiSessionValid":false,"externalAccounts":[]}}}
                """.trimIndent(),
            )
        }
        assertEquals(QrLoginPollResult.Approved, repository(server).poll(session))

        assertEquals(
            AccessTokenSession(AccessTokenPair(aniAccessToken = "access", expiresAtMillis = 99, bangumiAccessToken = null)),
            tokenRepository.session.first(),
        )
        assertEquals("refresh", tokenRepository.refreshToken.first())
    }

    @Test
    fun `poll 404 is expired and other errors throw`() = runTest {
        var status = HttpStatusCode.NotFound
        val server = Server { json("{}", status) }
        val repository = repository(server)

        assertEquals(QrLoginPollResult.Expired, repository.poll(session))
        status = HttpStatusCode.InternalServerError
        assertFailsWith<RepositoryException> { repository.poll(session) }
    }

    @Test
    fun `scan maps results`() = runTest {
        var status = HttpStatusCode.OK
        val server = Server { json("""{"deviceName":"TV","expiresAtMillis":7}""", status) }
        val repository = repository(server)

        assertEquals(QrLoginScanResult.Success("TV"), repository.scan("request-1"))
        assertEquals("/v2/users/qr-login/request-1/scan", server.requests.single().url.encodedPath)
        status = HttpStatusCode.NotFound
        assertEquals(QrLoginScanResult.Expired, repository.scan("request-1"))
        status = HttpStatusCode.Conflict
        assertEquals(QrLoginScanResult.AlreadyHandled, repository.scan("request-1"))
    }

    @Test
    fun `confirm maps results`() = runTest {
        var status = HttpStatusCode.NoContent
        val server = Server { respond("", status) }
        val repository = repository(server)

        assertTrue(repository.confirm("request-1", approve = true))
        assertTrue("true" in server.requests.single().body.toByteArray().decodeToString())
        status = HttpStatusCode.Conflict
        assertFalse(repository.confirm("request-1", approve = true))
        status = HttpStatusCode.NotFound
        assertFalse(repository.confirm("request-1", approve = false))
    }

    @Test
    fun `parseRequestId accepts the web link and the deep link only`() {
        assertEquals("abc", QrLoginRepository.parseRequestId("https://api.animeko.org/static/immutable/qrLogin.v1.html?requestId=abc"))
        assertEquals("abc", QrLoginRepository.parseRequestId(" ani://qr-login?requestId=abc\n"))
        assertNull(QrLoginRepository.parseRequestId("ani://bangumi-oauth-callback?requestId=abc"))
        assertNull(QrLoginRepository.parseRequestId("https://example.com/?requestId=abc"))
        assertNull(QrLoginRepository.parseRequestId("https://api.animeko.org/static/immutable/qrLogin.v1.html"))
        assertNull(QrLoginRepository.parseRequestId("not a url"))
    }
}
