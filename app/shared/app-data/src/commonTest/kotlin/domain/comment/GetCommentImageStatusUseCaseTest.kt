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
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.client.apis.CommentsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GetCommentImageStatusUseCaseTest {
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

    private fun MockRequestHandleScope.images(vararg images: String): HttpResponseData =
        respond(
            """{"images":[${images.joinToString(",")}]}""",
            HttpStatusCode.OK,
            headersOf("Content-Type", "application/json"),
        )

    private fun image(id: String, status: String, failureCode: String? = null): String {
        val failure = failureCode?.let { ""","failureCode":"$it"""" } ?: ""
        return """{"id":"$id","status":"$status","url":"${url(id)}"$failure}"""
    }

    private fun url(id: String) = "https://usercontent.myani.org/comment-images/u/$id"

    private suspend fun TestScope.check(server: Server, ids: List<String>): CommentImageStatusResult =
        GetCommentImageStatusUseCaseImpl(server.api, coroutineContext).invoke(ids)

    /**
     * 请求中的 `ids` 参数, 按逗号分开.
     */
    private val HttpRequestData.ids get() = url.parameters["ids"].orEmpty().split(",")

    @Test
    fun `maps every status and failure code`() = runTest {
        val ids = List(6) { id(it) }
        val server = Server {
            images(
                image(ids[0], "PROCESSING"),
                image(ids[1], "READY"),
                image(ids[2], "FAILED", "UNSUPPORTED_IMAGE"),
                image(ids[3], "FAILED", "TOO_LARGE"),
                image(ids[4], "FAILED", "INTERNAL"),
                image(ids[5], "FAILED", "NOT_FOUND"),
            )
        }

        val result = check(server, ids)

        assertEquals(
            CommentImageStatusResult.Ok(
                mapOf(
                    ids[0] to CommentImageProcessingStatus.Processing,
                    ids[1] to CommentImageProcessingStatus.Ready(url(ids[1])),
                    ids[2] to CommentImageProcessingStatus.Failed(CommentImageUploadResult.InvalidFormat),
                    ids[3] to CommentImageProcessingStatus.Failed(CommentImageUploadResult.InvalidFormat),
                    ids[4] to CommentImageProcessingStatus.Failed(CommentImageUploadResult.ProcessingFailed),
                    ids[5] to CommentImageProcessingStatus.Failed(CommentImageUploadResult.ProcessingFailed),
                ),
            ),
            result,
        )
        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v2/comments/images", request.url.encodedPath)
        assertEquals(ids, request.ids)
    }

    @Test
    fun `ids are deduplicated and sent in chunks of ten`() = runTest {
        val ids = List(23) { id(it) }
        val server = Server { request -> images(*request.ids.map { image(it, "PROCESSING") }.toTypedArray()) }

        val result = check(server, ids + ids[0])

        assertEquals(listOf(ids.subList(0, 10), ids.subList(10, 20), ids.subList(20, 23)), server.requests.map { it.ids })
        assertEquals(ids.toSet(), (result as CommentImageStatusResult.Ok).statuses.keys)
    }

    @Test
    fun `ids missing from the response are left out`() = runTest {
        val server = Server { images(image(id(0), "READY")) }

        assertEquals(
            CommentImageStatusResult.Ok(mapOf(id(0) to CommentImageProcessingStatus.Ready(url(id(0))))),
            check(server, listOf(id(0), id(1))),
        )
    }

    @Test
    fun `no ids sends no request`() = runTest {
        val server = Server { error("should not send request") }

        assertEquals(CommentImageStatusResult.Ok(emptyMap()), check(server, emptyList()))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `errors are unavailable`() = runTest {
        for (status in listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.BadRequest,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.ServiceUnavailable,
        )) {
            val server = Server { respond("error", status, headersOf("Content-Type", "text/plain")) }
            assertEquals(CommentImageStatusResult.Unavailable, check(server, listOf(id(0))), "$status")
        }
        assertEquals(
            CommentImageStatusResult.Unavailable,
            check(Server { throw IOException("connection reset") }, listOf(id(0))),
        )
        assertEquals(
            CommentImageStatusResult.Unavailable,
            check(Server { respond("not json", HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }, listOf(id(0))),
        )
    }

    @Test
    fun `a failing chunk makes the whole result unavailable`() = runTest {
        // 第二次请求 (第 11 个开始) 失败
        val server = Server { request ->
            if (request.ids.first() == id(10)) throw IOException("connection reset")
            images(*request.ids.map { image(it, "READY") }.toTypedArray())
        }

        assertEquals(CommentImageStatusResult.Unavailable, check(server, List(11) { id(it) }))
        assertEquals(2, server.requests.size)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val api = object : ApiInvoker<CommentsAniApi> {
            override suspend fun <R> invoke(action: suspend CommentsAniApi.() -> R): R {
                throw CancellationException("cancelled")
            }
        }

        assertFailsWith<CancellationException> {
            GetCommentImageStatusUseCaseImpl(api, coroutineContext).invoke(listOf(id(0)))
        }
    }

    private companion object {
        /**
         * 第 [index] 个图片标识, 64 位十六进制.
         */
        fun id(index: Int): String = index.toString(16).padStart(64, '0')
    }
}
