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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * 过滤广告时起播要等时间戳探测. 这里验证等待有上限, 且首片在探测期间已经下载, 播放器请求时不再重下;
 * 会话的所有请求共用一个 client, 连接在请求之间复用.
 *
 * 夹具 `withads.m3u8` 分三组: seg000-015, ad000-001, seg016-031; 探测的是 seg000, ad000, seg016.
 */
class HlsAdFilterStartupTest {
    private fun withPreparer(block: suspend (HlsFixtureOrigin, PlatformHlsPlaybackPreparer) -> Unit) = runBlocking {
        val origin = HlsFixtureOrigin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            block(origin, PlatformHlsPlaybackPreparer(provider))
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private fun String.segmentUris(): List<String> = lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

    @Test
    fun `a hanging probe does not hold up the playlist and keeps its group`() = withPreparer { origin, preparer ->
        // 远超探测上限, 也超过客户端的读超时 (30 秒), 只有探测上限能让它提前结束
        origin.latencyByPath["/hls/vod/seg016.ts"] = 60_000

        val (result, elapsed) = measureTimedValue {
            preparer.prepare(UriMediaData(origin.url("/hls/withads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        }
        val session = assertNotNull(result.session)
        try {
            // 上限 8 秒, 余量留给建 client 和 CI 机器的抖动
            assertTrue(elapsed.inWholeMilliseconds < 12_000, "prepare took $elapsed")

            val local = httpGet(result.data.uri).body.decodeToString()
            // 按时探测到的广告照删, 没探测到的那组正片留着
            assertFalse(local.contains("/ads/"), "ad group probed in time must be removed")
            assertEquals(32, local.segmentUris().size)
        } finally {
            session.close()
        }
    }

    /**
     * 会话从取主播放列表起就持有同一个 client. 逐个请求借还 client 的话, 归还时 client 被关闭, 而 Ktor 的 OkHttp 引擎
     * 关闭时会清空全进程共用的连接池里的空闲连接, 之后每个请求都要新建连接 (真实源站上每次都是一次 TLS 握手).
     * 同一进程里别处关闭 client 也会清空, 所以只比较本测试内紧接着发出的顺序请求.
     *
     * 夹具是 HTTP/1.1, 并发的探测和首片预缓存可能各开一条连接, 条数不固定; 确定的是: 变体列表复用取主播放列表的那条连接,
     * 之后逐个请求的分片也都落在已有的连接上.
     */
    @Test
    fun `session requests reuse the connections it already opened`() = withPreparer { origin, preparer ->
        origin.extraBodies["/hls/master-withads.m3u8"] = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=30000\nwithads.m3u8\n".encodeToByteArray()
        val result = preparer.prepare(UriMediaData(origin.url("/hls/master-withads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        val session = assertNotNull(result.session)
        try {
            val variantUri = httpGet(result.data.uri).body.decodeToString().segmentUris().single()
            val masterPort = origin.requests.single { it.path == "/hls/master-withads.m3u8" }.clientPort
            val segmentUris = httpGet(variantUri).body.decodeToString().segmentUris()
            assertEquals(masterPort, origin.requests.single { it.path == "/hls/withads.m3u8" }.clientPort)

            // 请求首片会等预缓存下完. 此后探测和预缓存都已结束, 它们的连接回到池里, 不再有并发请求
            withTimeout(5_000) {
                while (origin.countWhole("/hls/vod/seg000.ts") == 0) delay(10)
            }
            assertEquals(200, httpGet(segmentUris.first()).status)
            val portsBefore = origin.requests.map { it.clientPort }.toSet()

            for (uri in segmentUris.subList(1, 6)) {
                assertEquals(200, httpGet(uri).status)
            }
            val newPorts = origin.requests.map { it.clientPort }.toSet() - portsBefore
            assertEquals(emptySet(), newPorts, "sequential segment requests must reuse the session's connections")
        } finally {
            session.close()
        }
    }

    /**
     * 夹具源站是 HTTP/1.1, 每个并发请求各占一条连接. 探测不能对它放开到 64 路, 否则真实源站上就是 64 次 TLS 握手.
     */
    @Test
    fun `probes an HTTP 1 origin with a few connections`() = withPreparer { origin, preparer ->
        // 32 片各自成组, 探测 32 个不同地址. 延迟让并发的请求在源站上重叠, 峰值才看得出来
        origin.extraBodies["/hls/many-groups.m3u8"] = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:3")
            for (i in 0..31) {
                if (i > 0) appendLine("#EXT-X-DISCONTINUITY")
                appendLine("#EXTINF:3.000000,")
                appendLine("vod/seg%03d.ts".format(i))
            }
            appendLine("#EXT-X-ENDLIST")
        }.encodeToByteArray()
        origin.segmentLatencyMillis = 200

        val result = preparer.prepare(UriMediaData(origin.url("/hls/many-groups.m3u8")), HlsPlaybackOptions(filterSegments = true))
        try {
            assertEquals(32, origin.requests.count { "range" in it.headers }, "every group must be probed")
            val peak = origin.maxConcurrentRangeRequests.get()
            assertTrue(peak in 2..8, "probes in flight peaked at $peak")
        } finally {
            result.session?.close()
        }
    }

    @Test
    fun `the first two segments are downloaded once, while probing`() = withPreparer { origin, preparer ->
        // 只开过滤: 可能含广告的播放列表照样代理分片
        val filtered = preparer.prepare(UriMediaData(origin.url("/hls/withads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        try {
            val (firstUri, secondUri) = httpGet(filtered.data.uri).body.decodeToString().segmentUris()
            assertTrue(firstUri.startsWith("http://127.0.0.1:"), "expected a proxied segment, got $firstUri")
            // 播放器还没请求, 开头两片已经在下载: 播放器打开时两片都要读
            withTimeout(5_000) {
                while (origin.countWhole("/hls/vod/seg000.ts") == 0 || origin.countWhole("/hls/vod/seg001.ts") == 0) delay(10)
            }
            assertContentEquals(origin.bytesOf("/hls/vod/seg001.ts"), httpGet(secondUri).body)
            assertEquals(1, origin.countWhole("/hls/vod/seg001.ts"), "second segment must be served from the prefetch")
            assertContentEquals(origin.bytesOf("/hls/vod/seg000.ts"), httpGet(firstUri).body)
            assertEquals(1, origin.countWhole("/hls/vod/seg000.ts"), "player request must be served from the prefetch")
        } finally {
            filtered.session?.close()
        }
    }
}
