/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 在 iOS 上 (Kotlin/Native + Darwin 网络栈) 端到端验证 HLS 本地代理: 真实的 socket, 真实的 HTTP 请求.
 *
 * 代理的逻辑本身已由桌面端的 `RealHlsProxyTest` 用真实夹具详细覆盖 (同一份公共代码, 同一个 [KtorNetworkHlsProxyServer]);
 * 这里关注只有在 Native 上才会出问题的部分: socket 行为, 播放器中途断开, Darwin 引擎访问源站, 以及 URI 解析.
 */
class IosHlsProxyTest {
    private class Fixture(val origin: IosHlsTestOrigin, val preparer: PlatformHlsPlaybackPreparer)

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val origin = IosHlsTestOrigin.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            withTimeout(60_000) { Fixture(origin, PlatformHlsPlaybackPreparer(provider)).block() }
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    /** 10 片, 每片 3 秒. 第 5 片特别大, 用来验证大响应不会被截断. */
    private fun Fixture.installVod(): List<ByteArray> {
        val segments = (0 until 10).map { fakeTsBytes(seed = it, packets = if (it == 5) 20_000 else 1_500) }
        origin.routeText(
            "/vod/index.m3u8",
            buildString {
                append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:3\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n")
                segments.indices.forEach { append("#EXTINF:3.000000,\nseg$it.ts\n") }
                append("#EXT-X-ENDLIST\n")
            },
        )
        segments.forEachIndexed { index, bytes -> origin.route("/vod/seg$index.ts", bytes) }
        return segments
    }

    private suspend fun Fixture.prepareVod(): Pair<HlsPlaybackProxySession, List<String>> {
        val result = preparer.prepare(UriMediaData(origin.url("/vod/index.m3u8"), emptyMap()), HlsPlaybackOptions(proxySegments = true))
        val session = assertNotNull(result.session, "expected a proxy session")
        val playlist = assertNotNull(rawHttpGet(result.data.uri))
        assertEquals(200, playlist.status)
        val uris = playlist.body.decodeToString().lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        return session to uris
    }

    @Test
    fun `uses ktor network server on iOS`() {
        assertTrue(PlatformHlsProxyServerFactory === KtorNetworkHlsProxyServer.Factory)
    }

    @Test
    fun `playlist is rewritten to local segment routes and segments are forwarded byte exact`() = withFixture {
        val segments = installVod()
        val (session, uris) = prepareVod()
        session.use {
            assertEquals(10, uris.size)
            uris.forEach {
                assertTrue(it.startsWith("http://127.0.0.1:"), "expected local uri, got $it")
                assertTrue(it.endsWith(".ts"), "segment route must keep the extension: $it")
            }
            // 走的是流式转发 (未预缓存), 包括 3.7 MB 的大分片
            for (index in listOf(0, 5, 9)) {
                val response = assertNotNull(rawHttpGet(uris[index]))
                assertEquals(200, response.status)
                assertEquals(segments[index].size.toString(), response.headers["content-length"])
                assertContentEquals(segments[index], response.body, "segment $index")
            }
        }
    }

    @Test
    fun `prefetched segments are served from memory without hitting origin again`() = withFixture {
        val segments = installVod()
        val (session, uris) = prepareVod()
        session.use {
            // 12s..21s -> 分片 4, 5, 6
            session.setPrefetchRange(MediaTimeRange(12_000, 21_000))
            val progress = session.prefetchProgress.first { list -> list.size == 3 && list.all { it.state == ChunkState.DONE } }
            assertEquals(listOf(12_000L, 15_000L, 18_000L), progress.map { it.range.startMillis })
            assertEquals(1, origin.requestCount("/vod/seg5.ts"))

            val full = assertNotNull(rawHttpGet(uris[5]))
            assertEquals(200, full.status)
            assertContentEquals(segments[5], full.body)

            val partial = assertNotNull(rawHttpGet(uris[5], headers = mapOf("Range" to "bytes=188-563")))
            assertEquals(206, partial.status)
            assertEquals("bytes 188-563/${segments[5].size}", partial.headers["content-range"])
            assertContentEquals(segments[5].copyOfRange(188, 564), partial.body)

            assertEquals(1, origin.requestCount("/vod/seg5.ts"), "cached segment must not be fetched again")
            assertEquals(0, origin.requestCount("/vod/seg7.ts"), "segments outside the range must not be prefetched")
        }
    }

    @Test
    fun `player waits for in flight prefetch instead of downloading twice`() = withFixture {
        val segments = installVod()
        origin.segmentLatencyMillis = 1_000
        val (session, uris) = prepareVod()
        session.use {
            session.setPrefetchRange(MediaTimeRange(12_000, 14_000)) // 分片 4
            session.prefetchProgress.first { it.size == 1 }
            val response = assertNotNull(rawHttpGet(uris[4]))
            assertContentEquals(segments[4], response.body)
            assertEquals(1, origin.requestCount("/vod/seg4.ts"))
        }
    }

    /**
     * 播放器 seek 时会直接断开正在下载的分片. 在 Darwin 上往已关闭的 socket 写数据会触发 `SIGPIPE`,
     * 处理不当整个应用会被杀掉. 这个测试能跑完就说明进程没死, 并且代理之后仍能正常服务.
     */
    @Test
    fun `client disconnecting mid transfer does not break the proxy`() = withFixture {
        val segments = installVod()
        val (session, uris) = prepareVod()
        session.use {
            repeat(5) {
                assertNull(rawHttpGet(uris[5], abortAfterBytes = 64 * 1024))
            }
            // 预缓存命中的路径也试一遍
            session.setPrefetchRange(MediaTimeRange(15_000, 17_000)) // 分片 5
            session.prefetchProgress.first { list -> list.size == 1 && list.all { it.state == ChunkState.DONE } }
            repeat(5) {
                assertNull(rawHttpGet(uris[5], abortAfterBytes = 64 * 1024))
            }
            val response = assertNotNull(rawHttpGet(uris[5]))
            assertContentEquals(segments[5], response.body)
        }
    }

    @Test
    fun `origin errors are forwarded to the player`() = withFixture {
        installVod()
        origin.routeText(
            "/broken/index.m3u8",
            "#EXTM3U\n#EXT-X-TARGETDURATION:3\n#EXTINF:3.0,\nmissing0.ts\n#EXTINF:3.0,\nmissing1.ts\n#EXT-X-ENDLIST\n",
        )
        val result = preparer.prepare(UriMediaData(origin.url("/broken/index.m3u8"), emptyMap()), HlsPlaybackOptions(proxySegments = true))
        assertNotNull(result.session).use {
            val uris = assertNotNull(rawHttpGet(result.data.uri)).body.decodeToString()
                .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            assertEquals(404, assertNotNull(rawHttpGet(uris[0])).status)
        }
    }

    @Test
    fun `master playlist variants are routed through the proxy`() = withFixture {
        val segments = installVod()
        origin.routeText(
            "/master.m3u8",
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nvod/index.m3u8\n",
        )
        val result = preparer.prepare(UriMediaData(origin.url("/master.m3u8"), emptyMap()), HlsPlaybackOptions(proxySegments = true))
        assertNotNull(result.session).use {
            val variant = assertNotNull(rawHttpGet(result.data.uri)).body.decodeToString()
                .lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
            assertTrue(variant.startsWith("http://127.0.0.1:") && variant.endsWith(".m3u8"), variant)
            val uris = assertNotNull(rawHttpGet(variant)).body.decodeToString()
                .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            assertEquals(10, uris.size)
            assertContentEquals(segments[2], assertNotNull(rawHttpGet(uris[2])).body)
        }
    }

    @Test
    fun `closing the session releases the port`() = withFixture {
        installVod()
        val (session, uris) = prepareVod()
        session.close()
        assertFailsWith<Throwable> { rawHttpGet(uris[0]) }
    }

    /**
     * iOS 会在应用挂起期间回收监听 socket. 播放器持有的地址带着端口号, 所以必须在原端口上恢复.
     */
    @Test
    fun `server recovers on the same port after the listener breaks`() = runBlocking {
        val server = KtorNetworkHlsProxyServer.create()
        try {
            server.start { request, sink ->
                val body = request.path.encodeToByteArray()
                sink.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                sink.write(body)
            }
            val url = "http://127.0.0.1:${server.port}/playlist.m3u8"
            suspend fun awaitServing(): String = withTimeout(15_000) {
                var body: String? = null
                while (body == null) {
                    body = runCatching { rawHttpGet(url) }.getOrNull()?.takeIf { it.status == 200 }?.body?.decodeToString()
                    if (body == null) delay(50)
                }
                body
            }
            assertEquals("/playlist.m3u8", awaitServing())
            repeat(3) {
                server.breakListenerForTest()
                assertEquals("/playlist.m3u8", awaitServing())
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `non HLS and live playlists are left alone`() = withFixture {
        origin.routeText(
            "/live/index.m3u8",
            "#EXTM3U\n#EXT-X-TARGETDURATION:3\n#EXT-X-MEDIA-SEQUENCE:7\n#EXTINF:3.0,\nseg7.ts\n#EXTINF:3.0,\nseg8.ts\n",
        )
        val options = HlsPlaybackOptions(proxySegments = true)
        val live = preparer.prepare(UriMediaData(origin.url("/live/index.m3u8"), emptyMap()), options)
        assertNull(live.session)
        assertEquals(origin.url("/live/index.m3u8"), live.data.uri)
        val mp4 = preparer.prepare(UriMediaData(origin.url("/video.mp4"), emptyMap()), options)
        assertNull(mp4.session)
    }

    @Test
    fun `resolves uris like a browser`() {
        val base = "https://cdn.example.com/a/b/index.m3u8?token=1"
        assertEquals("https://cdn.example.com/a/b/seg0.ts", resolveHlsUri(base, "seg0.ts"))
        assertEquals("https://cdn.example.com/a/b/hd/seg0.ts?x=1", resolveHlsUri(base, "hd/seg0.ts?x=1"))
        assertEquals("https://cdn.example.com/a/seg0.ts", resolveHlsUri(base, "../seg0.ts"))
        assertEquals("https://cdn.example.com/root/seg0.ts", resolveHlsUri(base, "/root/seg0.ts"))
        assertEquals("https://other.example.com/seg0.ts", resolveHlsUri(base, "//other.example.com/seg0.ts"))
        assertEquals("http://other.example.com/seg0.ts", resolveHlsUri(base, "http://other.example.com/seg0.ts"))
    }
}
