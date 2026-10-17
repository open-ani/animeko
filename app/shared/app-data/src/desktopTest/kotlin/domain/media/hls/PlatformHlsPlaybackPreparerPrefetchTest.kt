/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformHlsPlaybackPreparerPrefetchTest {
    /** 代理在真实线程上工作, 等待时不能用 runTest 的虚拟时间. */
    private suspend fun <T> awaitReal(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(10_000) { block() } }

    // 4 个 10 秒分片的点播列表, 没有广告
    private val plainManifest = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:10")
        appendLine("#EXT-X-MEDIA-SEQUENCE:0")
        for (i in 0 until 4) {
            appendLine("#EXTINF:10.0,")
            appendLine("seg$i.ts")
        }
        append("#EXT-X-ENDLIST")
    }

    private fun segmentBytes(index: Int): ByteArray = ByteArray(1024 + index) { (index + it).toByte() }

    private fun createServer(): SegmentServer = SegmentServer(
        contentByPath = buildMap {
            put("/v/index.m3u8", plainManifest.toByteArray(StandardCharsets.UTF_8))
            for (i in 0 until 4) put("/v/seg$i.ts", segmentBytes(i))
        },
    )

    @Test
    fun `filter only mode leaves plain playlist untouched`() = runTest {
        val server = createServer()
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)
        try {
            val result = preparer.prepare(
                UriMediaData("${server.baseUrl}/v/index.m3u8"),
                HlsPlaybackOptions(filterSegments = true, proxySegments = false),
            )
            assertNull(result.session)
            assertEquals("${server.baseUrl}/v/index.m3u8", result.data.uri)
        } finally {
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `disabled options never touch the network`() = runTest {
        val server = createServer()
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)
        try {
            val result = preparer.prepare(UriMediaData("${server.baseUrl}/v/index.m3u8"), HlsPlaybackOptions.Disabled)
            assertNull(result.session)
            assertEquals(0, server.requestCount("/v/index.m3u8"))
        } finally {
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `proxies segments through localhost and streams them from remote`() = runTest {
        val server = createServer()
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)
        val result = preparer.prepare(
            UriMediaData("${server.baseUrl}/v/index.m3u8", headers = mapOf("Referer" to "https://media.example.com/watch")),
            HlsPlaybackOptions(proxySegments = true),
        )
        try {
            val session = assertNotNull(result.session)
            assertIs<HlsPlaybackProxySession>(session)
            val localManifest = URI(result.data.uri).toURL().readText()
            val segmentUris = localManifest.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            assertEquals(4, segmentUris.size)
            segmentUris.forEach { assertContains(it, "http://127.0.0.1:") }
            assertContains(localManifest, "#EXT-X-ENDLIST")

            val bytes = URI(segmentUris[2]).toURL().readBytes()
            assertContentEquals(segmentBytes(2), bytes)
            assertEquals(1, server.requestCount("/v/seg2.ts"))
            assertEquals("https://media.example.com/watch", server.lastReferer("/v/seg2.ts"))
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `prefetch downloads overlapping segments and serves them from cache`() = runTest {
        val server = createServer()
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)
        val result = preparer.prepare(UriMediaData("${server.baseUrl}/v/index.m3u8"), HlsPlaybackOptions(proxySegments = true))
        try {
            val session = assertNotNull(result.session)
            // 播放器先拉一次播放列表, 代理才知道时间轴
            val localManifest = URI(result.data.uri).toURL().readText()
            val segmentUris = localManifest.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

            // [15s, 25s) 覆盖 seg1 [10,20) 和 seg2 [20,30)
            session.setPrefetchRange(MediaTimeRange(15_000, 25_000))
            val done = awaitReal {
                session.prefetchProgress.first { list -> list.size == 2 && list.all { it.state == ChunkState.DONE } }
            }
            assertEquals(listOf(MediaTimeRange(10_000, 20_000), MediaTimeRange(20_000, 30_000)), done.map { it.range })
            assertEquals(1, server.requestCount("/v/seg1.ts"))
            assertEquals(1, server.requestCount("/v/seg2.ts"))
            assertEquals(0, server.requestCount("/v/seg0.ts"))
            assertEquals(0, server.requestCount("/v/seg3.ts"))

            // 播放器再请求预缓存过的分片时直接命中缓存, 不再访问远端
            assertContentEquals(segmentBytes(1), URI(segmentUris[1]).toURL().readBytes())
            assertEquals(1, server.requestCount("/v/seg1.ts"))

            // 取消预缓存后进度清空
            session.setPrefetchRange(null)
            assertTrue(awaitReal { session.prefetchProgress.first { it.isEmpty() } }.isEmpty())
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    @Test
    fun `range request on cached segment returns partial content`() = runTest {
        val server = createServer()
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val preparer = PlatformHlsPlaybackPreparer(provider)
        val result = preparer.prepare(UriMediaData("${server.baseUrl}/v/index.m3u8"), HlsPlaybackOptions(proxySegments = true))
        try {
            val session = assertNotNull(result.session)
            val localManifest = URI(result.data.uri).toURL().readText()
            val segmentUris = localManifest.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            session.setPrefetchRange(MediaTimeRange(0, 5_000))
            awaitReal { session.prefetchProgress.first { list -> list.isNotEmpty() && list.all { it.state == ChunkState.DONE } } }

            val connection = URI(segmentUris[0]).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Range", "bytes=10-19")
            assertEquals(206, connection.responseCode)
            assertEquals("bytes 10-19/1024", connection.getHeaderField("Content-Range"))
            assertContentEquals(segmentBytes(0).copyOfRange(10, 20), connection.inputStream.readBytes())
            assertFalse(server.requestCount("/v/seg0.ts") > 1)
        } finally {
            result.session?.close()
            provider.forceReleaseAll()
            server.close()
        }
    }

    /**
     * 按路径返回固定内容的最小 HTTP 服务, 记录每个路径的请求次数和 Referer.
     */
    private class SegmentServer(
        private val contentByPath: Map<String, ByteArray>,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val requestCounts = ConcurrentHashMap<String, Int>()
        private val referers = ConcurrentHashMap<String, String>()
        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

        fun requestCount(path: String): Int = requestCounts[path] ?: 0
        fun lastReferer(path: String): String? = referers[path]

        private val thread = thread(name = "SegmentServer-${serverSocket.localPort}", isDaemon = true) {
            while (!closed.get()) {
                try {
                    val socket = serverSocket.accept()
                    thread(isDaemon = true) {
                        socket.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))
                            val lines = buildList {
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                    add(line)
                                }
                            }
                            val path = lines.firstOrNull()?.substringAfter(" ")?.substringBefore(" ") ?: return@use
                            requestCounts.merge(path, 1, Int::plus)
                            lines.firstOrNull { it.startsWith("Referer:", ignoreCase = true) }
                                ?.substringAfter(":")?.trim()?.let { referers[path] = it }
                            s.getOutputStream().use { output ->
                                val body = contentByPath[path]
                                if (body == null) {
                                    output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                                } else {
                                    val type = if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t"
                                    output.write(
                                        "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                            .toByteArray(StandardCharsets.US_ASCII),
                                    )
                                    output.write(body)
                                }
                                output.flush()
                            }
                        }
                    }
                } catch (e: SocketException) {
                    if (!closed.get()) throw e
                }
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) serverSocket.close()
        }

        @Suppress("unused")
        private fun keepThreadReachable(): Thread = thread
    }
}
