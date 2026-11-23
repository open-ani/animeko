/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import com.sun.net.httpserver.HttpServer
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.UploadCommentImageUseCaseImpl
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.settings.NoProxyProvider
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 用生产环境的 [DefaultHttpClientProvider] 和特性顺序访问多个 Ani 服务器地址, 检查服务器的错误响应发出几次请求、调用方得到什么结果.
 *
 * 服务器对上传等请求每次都计入频率限制. 同一个请求被重发时, 后面的请求会被限流,
 * 调用方拿到的是最后一次响应, 第一次响应中的错误码就丢了.
 */
class AniApiErrorResponseHttpClientTest {
    private class Request(val server: Int, val path: String, val authorization: String?)

    private class Response(
        val status: HttpStatusCode,
        val body: String,
        val contentType: String = "text/plain",
        val headers: Map<String, String> = emptyMap(),
    )

    /**
     * [SERVER_COUNT] 个本地服务器, 依次作为 Ani 服务器的地址. [respond] 的参数是收到请求的服务器序号.
     */
    private class LocalAniServers(private val respond: (server: Int) -> Response) : AutoCloseable {
        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

        private val servers = List(SERVER_COUNT) { index ->
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    exchange.requestBody.use { it.readBytes() }
                    requests += Request(
                        index,
                        exchange.requestURI.path,
                        exchange.requestHeaders.getFirst("Authorization"),
                    )
                    val response = respond(index)
                    val bytes = response.body.encodeToByteArray()
                    exchange.responseHeaders.add("Content-Type", response.contentType)
                    response.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
                    exchange.sendResponseHeaders(response.status.value, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
        }

        val urls: List<Url> = servers.map { Url("http://127.0.0.1:${it.address.port}") }

        override fun close() {
            servers.forEach { it.stop(0) }
        }
    }

    /**
     * 每个服务器都以 [status] 和纯文本 [body] 回应.
     */
    private fun everyServer(status: HttpStatusCode, body: String) = LocalAniServers { Response(status, body) }

    private class LoggedInSession : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = MutableStateFlow(SessionState.Valid(bangumiConnected = false))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private fun uploadImage(servers: LocalAniServers): CommentImageUploadResult = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // 与 CommonKoinModule 中的 HttpClientProvider 顺序一致
        val provider = DefaultHttpClientProvider(
            NoProxyProvider,
            scope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                UseAniTokenFeatureHandler(flowOf(TOKEN), onRefresh = { null }),
                AniInstallIdFeatureHandler { "3f2b0c1e-8d4a-4b6f-9a7e-0c5d2e1f3a4b" },
                ServerListFeatureHandler(flowOf(servers.urls)),
                DistributionChannelFeatureHandler { "test" },
                ConvertSendCountExceedExceptionFeatureHandler,
                VersionExpiryFeatureHandler,
                SseFeatureHandler,
            ),
        )
        try {
            val api = AniApiProvider(provider.get(useAniToken = true, useAniInstallId = true))
            UploadCommentImageUseCaseImpl(api.commentsApi, LoggedInSession(), Dispatchers.IO).invoke(PNG)
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
        }
    }

    @Test
    fun `pending limit is sent once and reported`() {
        everyServer(HttpStatusCode.TooManyRequests, "HTTP 429: COMMENT_IMAGE_PENDING_LIMIT").use { servers ->
            assertEquals(CommentImageUploadResult.PendingLimit, uploadImage(servers))

            val request = servers.requests.single()
            assertEquals(0, request.server)
            assertEquals("/v2/comments/images", request.path)
            assertEquals("Bearer $TOKEN", request.authorization)
        }
    }

    @Test
    fun `retry answer is sent once and reported as a network error`() {
        everyServer(HttpStatusCode.Conflict, "HTTP 409: COMMENT_IMAGE_RETRY").use { servers ->
            assertEquals(CommentImageUploadResult.NetworkError, uploadImage(servers))
            assertEquals(1, servers.requests.size)
        }
    }

    @Test
    fun `rejected image is sent once and reported as invalid format`() {
        everyServer(HttpStatusCode.UnprocessableEntity, "HTTP 422: Unsupported image format").use { servers ->
            assertEquals(CommentImageUploadResult.InvalidFormat, uploadImage(servers))
            assertEquals(1, servers.requests.size)
        }
    }

    @Test
    fun `server error moves on to the next server`() {
        LocalAniServers { server ->
            if (server == 0) {
                Response(HttpStatusCode.ServiceUnavailable, "HTTP 503: Service Unavailable")
            } else {
                Response(
                    HttpStatusCode.OK,
                    """{"id":"$IMAGE_ID","status":"READY","url":"$IMAGE_URL","width":1,"height":1}""",
                    "application/json",
                )
            }
        }.use { servers ->
            assertEquals(CommentImageUploadResult.Ready(IMAGE_URL), uploadImage(servers))
            assertEquals(listOf(0, 1), servers.requests.map { it.server })
        }
    }

    @Test
    fun `image service unavailable is sent once and reported as a processing failure`() {
        // 每个地址都通向同一个后端: 换地址重发会把同一张图再传一遍, 每次都计入频率限制
        LocalAniServers {
            Response(
                HttpStatusCode.ServiceUnavailable,
                "HTTP 503: COMMENT_IMAGE_UNAVAILABLE",
                headers = mapOf("X-Ani-Error-Code" to "COMMENT_IMAGE_UNAVAILABLE"),
            )
        }.use { servers ->
            assertEquals(CommentImageUploadResult.ProcessingFailed, uploadImage(servers))
            assertEquals(listOf(0), servers.requests.map { it.server })
        }
    }

    @Test
    fun `failure on every server tries each server once`() {
        everyServer(HttpStatusCode.InternalServerError, "HTTP 500: Internal Server Error").use { servers ->
            uploadImage(servers)
            assertEquals(List(SERVER_COUNT) { it }, servers.requests.map { it.server })
        }
    }

    private companion object {
        const val SERVER_COUNT = 4
        const val TOKEN = "token"
        const val IMAGE_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val IMAGE_URL = "https://usercontent.myani.org/comment-images/u/$IMAGE_ID"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    }
}
