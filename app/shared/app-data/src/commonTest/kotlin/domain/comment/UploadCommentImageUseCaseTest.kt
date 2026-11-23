/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.CommentsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadCommentImageUseCaseTest {
    private class Server(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()

        val api: ApiInvoker<CommentsAniApi> = object : ApiInvoker<CommentsAniApi> {
            private val engine = MockEngine { request ->
                requests += request
                handler(request)
            }

            // 与生产的 HttpClient 一致: 4xx/5xx 抛出 ClientRequestException / ServerResponseException
            private val client = CommentsAniApi(
                baseUrl = "http://test",
                httpClientEngine = engine,
                httpClientConfig = { it.expectSuccess = true },
            )

            override suspend fun <R> invoke(action: suspend CommentsAniApi.() -> R): R = action(client)
        }
    }

    private class FakeSession(state: SessionState) : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = MutableStateFlow(state)
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private fun MockRequestHandleScope.text(body: String, status: HttpStatusCode): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "text/plain"))

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private suspend fun TestScope.upload(
        server: Server,
        image: ByteArray = PNG,
        session: SessionState = SessionState.Valid(bangumiConnected = false),
    ): CommentImageUploadResult =
        UploadCommentImageUseCaseImpl(server.api, FakeSession(session), coroutineContext).invoke(image)

    @Test
    fun `upload sends raw bytes and returns url of a ready image`() = runTest {
        val server = Server {
            json("""{"id":"$IMAGE_ID","status":"READY","url":"$IMAGE_URL","width":1,"height":1}""")
        }

        val result = upload(server)

        assertEquals(CommentImageUploadResult.Ready(IMAGE_URL), result)
        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v2/comments/images", request.url.encodedPath)
        assertContentEquals(PNG, request.body.toByteArray())
    }

    @Test
    fun `image still being converted returns its id and url`() = runTest {
        val server = Server {
            json("""{"id":"$IMAGE_ID","status":"PROCESSING","url":"$IMAGE_URL"}""", HttpStatusCode.Accepted)
        }

        assertEquals(CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL), upload(server))
    }

    @Test
    fun `result follows the status in the body rather than the http status`() = runTest {
        var body = """{"id":"$IMAGE_ID","status":"READY","url":"$IMAGE_URL","width":1,"height":1}"""
        var status = HttpStatusCode.Accepted
        val server = Server { json(body, status) }

        assertEquals(CommentImageUploadResult.Ready(IMAGE_URL), upload(server))
        body = """{"id":"$IMAGE_ID","status":"PROCESSING","url":"$IMAGE_URL"}"""
        status = HttpStatusCode.OK
        assertEquals(CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL), upload(server))
    }

    @Test
    fun `failed body is mapped by its failure code`() = runTest {
        var code = "UNSUPPORTED_IMAGE"
        val server = Server {
            json("""{"id":"$IMAGE_ID","status":"FAILED","url":"$IMAGE_URL","failureCode":"$code"}""")
        }

        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server))
        code = "TOO_LARGE"
        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server))
        code = "INTERNAL"
        assertEquals(CommentImageUploadResult.ProcessingFailed, upload(server))
    }

    @Test
    fun `accepts all supported formats`() = runTest {
        val server = Server { json("""{"id":"$IMAGE_ID","status":"READY","url":"u","width":1,"height":1}""") }
        for (image in listOf(PNG, JPEG, WEBP, GIF87A, GIF89A)) {
            assertEquals(CommentImageUploadResult.Ready("u"), upload(server, image))
        }
        assertEquals(5, server.requests.size)
    }

    @Test
    fun `too large image is rejected without request`() = runTest {
        val server = Server { error("should not send request") }
        val image = PNG.copyOf(UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES.toInt() + 1)

        assertEquals(CommentImageUploadResult.TooLarge, upload(server, image))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `unsupported format is rejected without request`() = runTest {
        val server = Server { error("should not send request") }

        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server, "BM not an image".encodeToByteArray()))
        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server, byteArrayOf()))
        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server, "GIF8".encodeToByteArray()))
        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server, HEIC))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `guest is not logged in without request`() = runTest {
        val server = Server { error("should not send request") }

        assertEquals(
            CommentImageUploadResult.NotLoggedIn,
            upload(server, session = SessionState.Invalid(InvalidSessionReason.NO_TOKEN)),
        )
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `saved session that failed to refresh still sends request`() = runTest {
        val server = Server { text("Token is not valid or has expired", HttpStatusCode.Unauthorized) }

        for (reason in listOf(InvalidSessionReason.NETWORK_ERROR, InvalidSessionReason.UNKNOWN)) {
            assertEquals(
                CommentImageUploadResult.NotLoggedIn,
                upload(server, session = SessionState.Invalid(reason)),
            )
        }
        assertEquals(2, server.requests.size)
    }

    @Test
    fun `maps server rejections`() = runTest {
        var status = HttpStatusCode.PayloadTooLarge
        val server = Server { text("rejected", status) }

        assertEquals(CommentImageUploadResult.TooLarge, upload(server))
        status = HttpStatusCode.UnprocessableEntity
        assertEquals(CommentImageUploadResult.InvalidFormat, upload(server))
        status = HttpStatusCode.TooManyRequests
        assertEquals(CommentImageUploadResult.RateLimited, upload(server))
        status = HttpStatusCode.Unauthorized
        assertEquals(CommentImageUploadResult.NotLoggedIn, upload(server))
        status = HttpStatusCode.Forbidden
        assertEquals(CommentImageUploadResult.Forbidden, upload(server))
    }

    @Test
    fun `pending limit is distinguished from rate limit by error code`() = runTest {
        var body = "HTTP 429: COMMENT_IMAGE_PENDING_LIMIT"
        val server = Server { text(body, HttpStatusCode.TooManyRequests) }

        assertEquals(CommentImageUploadResult.PendingLimit, upload(server))
        body = "HTTP 429: Too Many Requests: too many image uploads"
        assertEquals(CommentImageUploadResult.RateLimited, upload(server))
    }

    @Test
    fun `retry conflict is a network error`() = runTest {
        var body = "HTTP 409: COMMENT_IMAGE_RETRY"
        val server = Server { text(body, HttpStatusCode.Conflict) }

        assertEquals(CommentImageUploadResult.NetworkError, upload(server))
        body = "HTTP 409: Conflict"
        assertEquals(CommentImageUploadResult.UnknownError(body), upload(server))
    }

    @Test
    fun `other client errors carry server message`() = runTest {
        var body = "Bad image"
        val server = Server { text(body, HttpStatusCode.BadRequest) }

        assertEquals(CommentImageUploadResult.UnknownError(body), upload(server))

        body = ""
        assertEquals(CommentImageUploadResult.UnknownError(HttpStatusCode.BadRequest.toString()), upload(server))
    }

    @Test
    fun `unavailable image service is a processing failure`() = runTest {
        assertEquals(
            CommentImageUploadResult.ProcessingFailed,
            upload(Server { text("HTTP 503: COMMENT_IMAGE_UNAVAILABLE", HttpStatusCode.ServiceUnavailable) }),
        )
        assertEquals(
            CommentImageUploadResult.ProcessingFailed,
            upload(Server { text("Service Unavailable", HttpStatusCode.ServiceUnavailable) }),
        )
    }

    @Test
    fun `retryable errors are the ones not about the image itself`() {
        assertFalse(CommentImageUploadResult.TooLarge.isRetryable)
        assertFalse(CommentImageUploadResult.InvalidFormat.isRetryable)
        for (error in listOf(
            CommentImageUploadResult.NotLoggedIn,
            CommentImageUploadResult.Forbidden,
            CommentImageUploadResult.RateLimited,
            CommentImageUploadResult.PendingLimit,
            CommentImageUploadResult.NetworkError,
            CommentImageUploadResult.ProcessingFailed,
            CommentImageUploadResult.UnknownError("x"),
        )) {
            assertTrue(error.isRetryable, "$error")
        }
    }

    @Test
    fun `server errors and IO errors are network errors`() = runTest {
        assertEquals(
            CommentImageUploadResult.NetworkError,
            upload(Server { text("oops", HttpStatusCode.InternalServerError) }),
        )
        assertEquals(
            CommentImageUploadResult.NetworkError,
            upload(Server { throw IOException("connection reset") }),
        )
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val api = object : ApiInvoker<CommentsAniApi> {
            override suspend fun <R> invoke(action: suspend CommentsAniApi.() -> R): R {
                throw CancellationException("cancelled")
            }
        }

        assertFailsWith<CancellationException> {
            UploadCommentImageUseCaseImpl(
                api,
                FakeSession(SessionState.Valid(bangumiConnected = false)),
                coroutineContext,
            ).invoke(PNG)
        }
    }

    private companion object {
        const val IMAGE_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val IMAGE_URL = "https://usercontent.myani.org/comment-images/u/$IMAGE_ID"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()
        val GIF87A = "GIF87a".encodeToByteArray()
        val GIF89A = "GIF89a".encodeToByteArray()
        val HEIC = "\u0000\u0000\u0000\u0018ftypheic\u0000\u0000\u0000\u0000".encodeToByteArray()
    }
}
