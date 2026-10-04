/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ParallelDownloadTest {
    @Test
    fun `server ignoring range is downloaded in one request`() = runTest {
        val content = ByteArray(3000) { it.toByte() }
        val requests = AtomicInteger()
        val client = mockClient { _ ->
            requests.incrementAndGet()
            respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, content.size.toString()))
        }
        withTempDir { dir ->
            val target = dir.resolve("a.zip")
            val reports = Collections.synchronizedList(mutableListOf<Pair<Long, Long?>>())
            client.downloadToFile("https://example.com/a.zip", target) { downloaded, total ->
                reports += downloaded to total
            }
            assertContentEquals(content, target.readBytes())
            assertEquals(1, requests.get())
            assertEquals(3000L to 3000L, reports.last())
        }
    }

    @Test
    fun `downloads chunks in parallel when the server supports range`() = runTest {
        val content = ByteArray((2 * DOWNLOAD_CHUNK_BYTES + 3).toInt()) { (it * 31).toByte() }
        val ranges = Collections.synchronizedList(mutableListOf<String>())
        val chunkRequests = AtomicInteger()
        val allChunksRequested = CompletableDeferred<Unit>()
        val client = mockClient { request ->
            val range = assertNotNull(request.headers[HttpHeaders.Range])
            ranges += range
            if (range != "bytes=0-0") {
                // 3 块的请求都到达后才响应, 逐块下载会在这里超时
                if (chunkRequests.incrementAndGet() == 3) allChunksRequested.complete(Unit)
                withTimeout(10.seconds) { allChunksRequested.await() }
            }
            respondRange(content, range)
        }
        withTempDir { dir ->
            val target = dir.resolve("a.zip")
            val reports = Collections.synchronizedList(mutableListOf<Pair<Long, Long?>>())
            client.downloadToFile("https://example.com/a.zip", target) { downloaded, total ->
                reports += downloaded to total
            }
            assertContentEquals(content, target.readBytes())
            assertEquals(listOf("a.zip"), dir.list().map { it.name })
            val chunk = DOWNLOAD_CHUNK_BYTES
            assertEquals("bytes=0-0", ranges.first())
            assertEquals(
                setOf("bytes=0-${chunk - 1}", "bytes=$chunk-${2 * chunk - 1}", "bytes=${2 * chunk}-${2 * chunk + 2}"),
                ranges.drop(1).toSet(),
            )
            assertEquals(content.size.toLong() to content.size.toLong(), reports.last())
        }
    }

    @Test
    fun `truncated chunk is retried on its own`() = runTest {
        val content = ByteArray((2 * DOWNLOAD_CHUNK_BYTES).toInt()) { it.toByte() }
        val secondChunk = "bytes=$DOWNLOAD_CHUNK_BYTES-${2 * DOWNLOAD_CHUNK_BYTES - 1}"
        val secondChunkAttempts = AtomicInteger()
        val client = mockClient { request ->
            val range = request.headers[HttpHeaders.Range]!!
            if (range == secondChunk && secondChunkAttempts.incrementAndGet() == 1) {
                // 只给一半数据, 模拟连接中途断开
                val start = DOWNLOAD_CHUNK_BYTES.toInt()
                respond(
                    content.copyOfRange(start, start + start / 2),
                    HttpStatusCode.PartialContent,
                    headersOf(HttpHeaders.ContentRange, "bytes $start-${2 * start - 1}/${content.size}"),
                )
            } else {
                respondRange(content, range)
            }
        }
        withTempDir { dir ->
            val target = dir.resolve("a.zip")
            val reports = Collections.synchronizedList(mutableListOf<Pair<Long, Long?>>())
            client.downloadToFile("https://example.com/a.zip", target) { downloaded, total ->
                reports += downloaded to total
            }
            assertContentEquals(content, target.readBytes())
            assertEquals(2, secondChunkAttempts.get())
            // 失败那次计入的字节已撤回
            assertEquals(content.size.toLong() to content.size.toLong(), reports.last())
        }
    }

    @Test
    fun `client error fails the download without retrying and removes chunk files`() = runTest {
        val content = ByteArray((3 * DOWNLOAD_CHUNK_BYTES).toInt())
        val forbidden = AtomicInteger()
        val client = mockClient { request ->
            val range = request.headers[HttpHeaders.Range]!!
            if (range.startsWith("bytes=$DOWNLOAD_CHUNK_BYTES-")) {
                forbidden.incrementAndGet()
                respond("expired", HttpStatusCode.Forbidden)
            } else {
                respondRange(content, range)
            }
        }
        withTempDir { dir ->
            val e = assertFailsWith<DownloadHttpException> {
                client.downloadToFile("https://example.com/a.zip", dir.resolve("a.zip"))
            }
            assertEquals(HttpStatusCode.Forbidden, e.status)
            assertEquals(1, forbidden.get())
            assertTrue(dir.list().none { it.name.contains(".part") })
        }
    }

    @Test
    fun `failure status surfaces`() = runTest {
        val client = mockClient(followRedirects = false, expectSuccess = false) { _ ->
            respond("gone", HttpStatusCode.NotFound)
        }
        withTempDir { dir ->
            val e = assertFailsWith<DownloadHttpException> {
                client.downloadToFile("https://example.com/a.zip", dir.resolve("a.zip"))
            }
            assertEquals(HttpStatusCode.NotFound, e.status)
        }
    }

    @Test
    fun `follows relative and absolute redirects and rejects loops`() = runTest {
        val content = byteArrayOf(1, 2, 3)
        val visited = mutableListOf<String>()
        val client = mockClient(followRedirects = false, expectSuccess = false) { request ->
            visited += request.url.toString()
            when (request.url.encodedPath) {
                "/start" -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/next?x=1"))
                "/next" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://objects.example.com/final"),
                )

                "/final" -> respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "3"))
                "/loop" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop"))
                else -> error("Unexpected request: ${request.url}")
            }
        }
        withTempDir { dir ->
            val target = dir.resolve("a.dmg")
            client.downloadToFile("https://github.com/start", target)
            assertContentEquals(content, target.readBytes())
            assertEquals(
                listOf("https://github.com/start", "https://github.com/next?x=1", "https://objects.example.com/final"),
                visited,
            )

            assertFailsWith<DownloadHttpException> {
                client.downloadToFile("https://github.com/loop", dir.resolve("b.dmg"))
            }
        }
    }

    /**
     * 默认配置与自动更新使用的 client 一致: 自动跟随重定向, 非 2xx 抛异常.
     */
    private fun mockClient(
        followRedirects: Boolean = true,
        expectSuccess: Boolean = true,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine { request -> handler(request) }) {
        this.followRedirects = followRedirects
        this.expectSuccess = expectSuccess
    }

    private fun MockRequestHandleScope.respondRange(content: ByteArray, range: String): HttpResponseData {
        val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
        return respond(
            content.copyOfRange(start, end + 1),
            HttpStatusCode.PartialContent,
            headersOf(HttpHeaders.ContentRange, "bytes $start-$end/${content.size}"),
        )
    }

    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("parallel-download-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
