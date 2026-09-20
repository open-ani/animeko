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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.media.player.prefetch.PrefetchSegmentInfo
import me.him188.ani.app.domain.settings.NoProxyProvider
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import org.openani.mediamp.source.UriMediaData
import java.io.File
import java.net.ConnectException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 用 ffmpeg 生成的真实 HLS 流 (见 `src/androidDeviceTest/assets/hls/generate.sh`, 与 Android 设备测试共用) 端到端测试 [PlatformHlsPlaybackPreparer] 的本地代理:
 * 播放列表改写、分片转发、Range、错误透传、以及按时间范围预缓存.
 *
 * 源站是 [HlsFixtureOrigin]; 代理对外的行为通过 JDK HTTP 客户端观察, 不依赖任何播放器.
 *
 * 夹具时间轴: `vod` 96 秒, 3 秒一片 (seg000..seg031); 其余变体 60 秒, 6 秒一片.
 */
class RealHlsProxyTest {
    private class Fixture(
        val origin: HlsFixtureOrigin,
        val preparer: PlatformHlsPlaybackPreparer,
        val scope: CoroutineScope,
    )

    private fun withFixture(
        segmentCacheMaxBytes: Long = PlatformHlsPlaybackPreparer.DEFAULT_SEGMENT_CACHE_MAX_BYTES,
        block: suspend Fixture.() -> Unit,
    ) = runBlocking {
        val origin = HlsFixtureOrigin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            Fixture(origin, PlatformHlsPlaybackPreparer(provider, segmentCacheMaxBytes), scope).block()
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private suspend fun Fixture.prepare(
        path: String,
        options: HlsPlaybackOptions = HlsPlaybackOptions(proxySegments = true),
        headers: Map<String, String> = emptyMap(),
    ): HlsPlaybackPreparerResult = preparer.prepare(UriMediaData(origin.url(path), headers), options)

    private fun HlsPlaybackPreparerResult.session(): HlsPlaybackProxySession = assertNotNull(session, "expected a proxy session")

    private fun String.segmentUris(): List<String> = lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

    private fun String.parseMedia(base: String): M3u8Playlist.MediaPlaylist =
        DefaultM3u8Parser.parse(this, base) as M3u8Playlist.MediaPlaylist

    private fun text(result: HttpResult): String = result.body.toString(StandardCharsets.UTF_8)

    private fun assertLocal(uri: String) {
        assertTrue(uri.startsWith("http://127.0.0.1:"), "expected local uri, got $uri")
    }

    private fun assertValidTs(bytes: ByteArray, what: String) {
        assertTrue(bytes.isNotEmpty() && bytes.size % 188 == 0, "$what: size ${bytes.size} is not a multiple of 188")
        for (i in bytes.indices step 188) {
            assertEquals(0x47.toByte(), bytes[i], "$what: missing TS sync byte at $i")
        }
    }

    private suspend fun <T> awaitReal(timeoutMillis: Long = 15_000, block: suspend () -> T): T =
        withTimeout(timeoutMillis) { block() }

    private suspend fun HlsPlaybackProxySession.awaitAllDone(expectedCount: Int): List<PrefetchSegmentInfo> = awaitReal {
        prefetchProgress.first { list -> list.size == expectedCount && list.all { it.state == ChunkState.DONE } }
    }

    private fun vodRange(index: Int) = MediaTimeRange(index * 3_000L, (index + 1) * 3_000L)

    // ---------------- 播放列表改写与分片转发 ----------------

    @Test
    fun `vod playlist is rewritten to local segment routes and keeps tags and durations`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8", headers = mapOf("Referer" to "https://site.example/watch/1"))
        val session = result.session()
        try {
            assertLocal(result.data.uri)
            assertEquals(mapOf("Referer" to "https://site.example/watch/1"), result.data.headers)
            assertEquals("https://site.example/watch/1", origin.lastHeaders("/hls/vod/index.m3u8")?.get("referer"))

            val localText = text(httpGet(result.data.uri))
            val originText = origin.bytesOf("/hls/vod/index.m3u8").toString(StandardCharsets.UTF_8)
            val local = localText.parseMedia(result.data.uri)
            val remote = originText.parseMedia(origin.url("/hls/vod/index.m3u8"))

            assertEquals(32, local.segments.size)
            assertEquals(remote.segments.map { it.duration }, local.segments.map { it.duration })
            assertEquals(remote.targetDuration, local.targetDuration)
            assertTrue(local.isEndlist)
            assertTrue(localText.contains("#EXT-X-VERSION:3"))
            assertTrue(localText.contains("#EXT-X-PLAYLIST-TYPE:VOD"))
            val uris = localText.segmentUris()
            assertEquals(32, uris.size)
            uris.forEach { assertLocal(it); assertTrue(it.contains("/segment/"), it) }
            // FFmpeg 的 HLS 解复用器按扩展名白名单校验分片地址, 本地路由必须保留原扩展名
            uris.forEach { assertTrue(it.endsWith(".ts"), it) }
            assertEquals(uris.size, uris.distinct().size, "segment routes must be unique")
            // 播放器再次请求播放列表也应得到同样的内容
            assertEquals(localText, text(httpGet(result.data.uri)))
        } finally {
            session.close()
        }
    }

    @Test
    fun `every proxied segment is byte identical to origin and is valid mpeg ts`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8", headers = mapOf("Referer" to "https://site.example/watch/1", "X-Token" to "abc"))
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            for ((index, uri) in uris.withIndex()) {
                val originPath = "/hls/vod/seg%03d.ts".format(index)
                val response = httpGet(uri)
                assertEquals(200, response.status, "segment $index")
                assertEquals("video/mp2t", response.headers["content-type"], "segment $index")
                assertEquals(response.body.size.toString(), response.headers["content-length"], "segment $index")
                assertContentEquals(origin.bytesOf(originPath), response.body, "segment $index")
                assertValidTs(response.body, "segment $index")
                assertEquals(1, origin.count(originPath), "segment $index should be fetched from origin exactly once")
                val forwarded = assertNotNull(origin.lastHeaders(originPath))
                assertEquals("https://site.example/watch/1", forwarded["referer"])
                assertEquals("abc", forwarded["x-token"])
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun `concurrent segment requests are all served correctly`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            val start = CountDownLatch(1)
            val failures = Collections.synchronizedList(mutableListOf<Throwable>())
            val threads = (0 until 16).map { index ->
                thread {
                    start.await()
                    try {
                        val response = httpGet(uris[index])
                        assertEquals(200, response.status)
                        assertContentEquals(origin.bytesOf("/hls/vod/seg%03d.ts".format(index)), response.body)
                    } catch (e: Throwable) {
                        failures += e
                    }
                }
            }
            start.countDown()
            threads.forEach { it.join(20_000) }
            assertTrue(failures.isEmpty(), "failures: $failures")
            for (index in 0 until 16) assertEquals(1, origin.count("/hls/vod/seg%03d.ts".format(index)))
        } finally {
            session.close()
        }
    }

    @Test
    fun `master playlist routes variants locally and prefetch follows the last served variant`() = withFixture {
        val result = prepare("/hls/master.m3u8")
        val session = result.session()
        try {
            val localMaster = text(httpGet(result.data.uri))
            assertTrue(localMaster.contains("#EXT-X-STREAM-INF:BANDWIDTH=30000"))
            assertTrue(localMaster.contains("#EXT-X-STREAM-INF:BANDWIDTH=50000"))
            val variants = localMaster.segmentUris()
            assertEquals(2, variants.size)
            variants.forEach { assertLocal(it) }

            val low = text(httpGet(variants[0]))
            val lowUris = low.segmentUris()
            assertEquals(32, lowUris.size)
            assertContentEquals(origin.bytesOf("/hls/vod/seg000.ts"), httpGet(lowUris[0]).body)

            val high = text(httpGet(variants[1]))
            val highUris = high.segmentUris()
            assertEquals(10, highUris.size)
            assertContentEquals(origin.bytesOf("/hls/high/seg003.ts"), httpGet(highUris[3]).body)

            // 最近提供的是 high, 预缓存应作用于 high 的时间轴 (6 秒一片): [7s, 13s) -> seg001, seg002
            session.setPrefetchRange(MediaTimeRange(7_000, 13_000))
            val done = session.awaitAllDone(2)
            assertEquals(listOf(MediaTimeRange(6_000, 12_000), MediaTimeRange(12_000, 18_000)), done.map { it.range })
            assertEquals(1, origin.count("/hls/high/seg001.ts"))
            assertEquals(1, origin.count("/hls/high/seg002.ts"))
            assertEquals(0, origin.count("/hls/vod/seg002.ts"))
            assertEquals(0, origin.count("/hls/vod/seg003.ts"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `aes encrypted playlist keeps absolute key uri and forwards ciphertext that decrypts to valid ts`() = withFixture {
        val result = prepare("/hls/aes/index.m3u8")
        val session = result.session()
        try {
            val local = text(httpGet(result.data.uri))
            val keyLine = local.lineSequence().first { it.startsWith("#EXT-X-KEY") }
            assertEquals(
                "#EXT-X-KEY:METHOD=AES-128,URI=\"${origin.url("/hls/aes/key.bin")}\",IV=0x000102030405060708090a0b0c0d0e0f",
                keyLine,
            )
            val uris = local.segmentUris()
            assertEquals(10, uris.size)
            val cipherBytes = httpGet(uris[4]).body
            assertContentEquals(origin.bytesOf("/hls/aes/seg004.ts"), cipherBytes)

            // 播放器会自己拿密钥解密; 这里模拟一遍, 证明转发的密文是完整的
            val key = httpGet(origin.url("/hls/aes/key.bin")).body
            assertEquals(16, key.size)
            val iv = ByteArray(16) { it.toByte() }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = cipher.doFinal(cipherBytes)
            assertValidTs(plain, "decrypted aes segment 4")
        } finally {
            session.close()
        }
    }

    @Test
    fun `fmp4 playlist keeps absolute init segment uri and proxies media segments`() = withFixture {
        val result = prepare("/hls/fmp4/index.m3u8")
        val session = result.session()
        try {
            val local = text(httpGet(result.data.uri))
            val mapLine = local.lineSequence().first { it.startsWith("#EXT-X-MAP") }
            assertEquals("#EXT-X-MAP:URI=\"${origin.url("/hls/fmp4/init.mp4")}\"", mapLine)
            val init = httpGet(origin.url("/hls/fmp4/init.mp4")).body
            assertTrue(init.toString(StandardCharsets.ISO_8859_1).contains("ftyp"), "init segment should be an MP4")

            val uris = local.segmentUris()
            assertEquals(10, uris.size)
            uris.forEach { assertTrue(it.endsWith(".m4s"), it) }
            for ((index, uri) in uris.withIndex()) {
                val response = httpGet(uri)
                assertEquals(200, response.status)
                assertEquals("video/mp4", response.headers["content-type"], "segment $index")
                assertContentEquals(origin.bytesOf("/hls/fmp4/seg%03d.m4s".format(index)), response.body)
                assertTrue(response.body.toString(StandardCharsets.ISO_8859_1).contains("moof"), "segment $index should contain moof")
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun `byterange single file and live playlists are not proxied`() = withFixture {
        val single = prepare("/hls/single/index.m3u8")
        assertNull(single.session)
        assertEquals(origin.url("/hls/single/index.m3u8"), single.data.uri)

        val live = prepare("/hls/live/index.m3u8")
        assertNull(live.session)
        assertEquals(origin.url("/hls/live/index.m3u8"), live.data.uri)

        val notHls = prepare("/hls/vod/seg000.ts")
        assertNull(notHls.session)
        assertEquals(0, origin.count("/hls/vod/seg000.ts"), "non-m3u8 uri must not be fetched")
    }

    @Test
    fun `range request on uncached segment is forwarded to origin and answered with 206`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uri = text(httpGet(result.data.uri)).segmentUris()[2]
            val full = origin.bytesOf("/hls/vod/seg002.ts")
            val response = httpGet(uri, mapOf("Range" to "bytes=188-563"))
            assertEquals(206, response.status)
            assertEquals("bytes 188-563/${full.size}", response.headers["content-range"])
            assertContentEquals(full.copyOfRange(188, 564), response.body)
            assertEquals("bytes=188-563", origin.lastHeaders("/hls/vod/seg002.ts")?.get("range"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `origin error status is passed through`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            origin.failPaths["/hls/vod/seg005.ts"] = 404
            origin.failPaths["/hls/vod/seg006.ts"] = 500
            assertEquals(404, httpGet(uris[5]).status)
            assertEquals(500, httpGet(uris[6]).status)
            // 源站恢复后可以正常取到
            origin.failPaths.clear()
            assertContentEquals(origin.bytesOf("/hls/vod/seg005.ts"), httpGet(uris[5]).body)
        } finally {
            session.close()
        }
    }

    @Test
    fun `chunked origin responses are forwarded intact`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            origin.chunkedPaths += "/hls/vod/seg003.ts"
            val uri = text(httpGet(result.data.uri)).segmentUris()[3]
            val response = httpGet(uri)
            assertEquals(200, response.status)
            assertNull(response.headers["content-length"], "proxy cannot know the length of a chunked upstream body")
            assertContentEquals(origin.bytesOf("/hls/vod/seg003.ts"), response.body)
            assertValidTs(response.body, "chunked segment 3")
        } finally {
            session.close()
        }
    }

    @Test
    fun `closing the session releases the port`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        val uris = text(httpGet(result.data.uri)).segmentUris()
        session.close()
        assertFailsWith<ConnectException> { httpGet(result.data.uri) }
        assertFailsWith<ConnectException> { httpGet(uris[0]) }
        // 重复 close 无副作用
        session.close()
    }

    // ---------------- 预缓存 ----------------

    @Test
    fun `prefetch downloads exactly the overlapping segments in order and serves them from cache`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        val recorder = scope.recordProgress(session)
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()

            // [20s, 27.5s) 与 seg006 [18,21) seg007 seg008 seg009 [27,30) 重叠
            session.setPrefetchRange(MediaTimeRange(20_000, 27_500))
            val done = session.awaitAllDone(4)
            assertEquals((6..9).map { vodRange(it) }, done.map { it.range })

            for (index in 6..9) assertEquals(1, origin.count("/hls/vod/seg%03d.ts".format(index)), "seg $index")
            for (index in listOf(0, 5, 10, 31)) assertEquals(0, origin.count("/hls/vod/seg%03d.ts".format(index)), "seg $index")

            // 进度按顺序完成: 第一次出现 DONE 的必须是 seg006, 且 DONE 数量单调不减
            val snapshots = recorder.snapshots()
            assertTrue(snapshots.first().all { it.state == ChunkState.DOWNLOADING }, "first emission should be all DOWNLOADING")
            val doneCounts = snapshots.map { list -> list.count { it.state == ChunkState.DONE } }
            assertEquals(doneCounts.sorted(), doneCounts, "DONE count must be monotonic")
            val firstDone = snapshots.first { list -> list.any { it.state == ChunkState.DONE } }
            assertEquals(vodRange(6), firstDone.first { it.state == ChunkState.DONE }.range)
            snapshots.forEach { list ->
                val states = list.map { it.state }
                val firstNotDone = states.indexOfFirst { it != ChunkState.DONE }
                if (firstNotDone >= 0) {
                    assertTrue(states.drop(firstNotDone).none { it == ChunkState.DONE }, "segments complete in order: $states")
                }
            }

            // 播放器请求预缓存过的分片时直接命中缓存
            for (index in 6..9) {
                val response = httpGet(uris[index])
                assertEquals(200, response.status)
                assertContentEquals(origin.bytesOf("/hls/vod/seg%03d.ts".format(index)), response.body)
                assertEquals(1, origin.count("/hls/vod/seg%03d.ts".format(index)), "seg $index must be served from cache")
            }
            // 未预缓存的仍走源站
            httpGet(uris[10])
            assertEquals(1, origin.count("/hls/vod/seg010.ts"))
        } finally {
            recorder.job.cancel()
            session.close()
        }
    }

    @Test
    fun `changing the range replaces progress and downloads only the new segments`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            session.setPrefetchRange(vodRange(0))
            session.awaitAllDone(1)

            session.setPrefetchRange(vodRange(10))
            val done = session.awaitAllDone(1)
            assertEquals(listOf(vodRange(10)), done.map { it.range })
            assertEquals(1, origin.count("/hls/vod/seg000.ts"))
            assertEquals(1, origin.count("/hls/vod/seg010.ts"))

            // 上一次预缓存的分片仍在缓存里 (未超出上限)
            httpGet(uris[0])
            assertEquals(1, origin.count("/hls/vod/seg000.ts"))

            // 设置相同范围不会重新下载
            session.setPrefetchRange(vodRange(10))
            session.awaitAllDone(1)
            assertEquals(1, origin.count("/hls/vod/seg010.ts"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `range set before the playlist is served is applied once the playlist is requested`() = withFixture {
        val result = prepare("/hls/master.m3u8")
        val session = result.session()
        try {
            // 主播放列表阶段还不知道分片时间轴
            session.setPrefetchRange(MediaTimeRange(0, 3_000))
            assertEquals(emptyList(), session.prefetchProgress.first())

            val variants = text(httpGet(result.data.uri)).segmentUris()
            httpGet(variants[0]) // vod
            val done = session.awaitAllDone(1)
            assertEquals(listOf(vodRange(0)), done.map { it.range })
            assertEquals(1, origin.count("/hls/vod/seg000.ts"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `cancelling prefetch clears progress but keeps cached segments`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            session.setPrefetchRange(MediaTimeRange(0, 6_000))
            session.awaitAllDone(2)

            session.setPrefetchRange(null)
            awaitReal { session.prefetchProgress.first { it.isEmpty() } }
            httpGet(uris[0]); httpGet(uris[1])
            assertEquals(1, origin.count("/hls/vod/seg000.ts"))
            assertEquals(1, origin.count("/hls/vod/seg001.ts"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `failed segment is dropped from progress and does not block the rest`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            origin.failPaths["/hls/vod/seg007.ts"] = 500
            session.setPrefetchRange(MediaTimeRange(18_000, 27_000)) // seg006, seg007, seg008
            val done = session.awaitAllDone(2)
            assertEquals(listOf(vodRange(6), vodRange(8)), done.map { it.range })
            assertEquals(1, origin.count("/hls/vod/seg007.ts"))

            // 源站恢复后播放器请求该分片, 从源站取
            origin.failPaths.clear()
            val response = httpGet(uris[7])
            assertEquals(200, response.status)
            assertContentEquals(origin.bytesOf("/hls/vod/seg007.ts"), response.body)
            assertEquals(2, origin.count("/hls/vod/seg007.ts"))
        } finally {
            session.close()
        }
    }

    @Test
    fun `cache evicts segments that are no longer requested once over the limit`() = withFixture(segmentCacheMaxBytes = 1) {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            session.setPrefetchRange(MediaTimeRange(0, 6_000)) // seg000, seg001 (被当前请求引用, 不淘汰)
            session.awaitAllDone(2)
            httpGet(uris[0])
            assertEquals(1, origin.count("/hls/vod/seg000.ts"), "pinned segment served from cache")

            session.setPrefetchRange(vodRange(10)) // seg000/seg001 不再被引用, 超限后淘汰
            session.awaitAllDone(1)
            httpGet(uris[0])
            assertEquals(2, origin.count("/hls/vod/seg000.ts"), "evicted segment is fetched from origin again")
            httpGet(uris[10])
            assertEquals(1, origin.count("/hls/vod/seg010.ts"), "current segment still cached")
        } finally {
            session.close()
        }
    }

    @Test
    fun `prefetch follows the filtered timeline when ad filtering is enabled`() = withFixture {
        // 不过滤: 播放列表含 34 片, [48s, 51s) 落在第一片广告上
        val unfiltered = prepare("/hls/withads.m3u8", HlsPlaybackOptions(filterSegments = false, proxySegments = true))
        val unfilteredSession = unfiltered.session()
        try {
            val local = text(httpGet(unfiltered.data.uri))
            assertEquals(34, local.segmentUris().size)
            unfilteredSession.setPrefetchRange(MediaTimeRange(48_000, 51_000))
            unfilteredSession.awaitAllDone(1)
            assertEquals(1, origin.count("/hls/ads/ad000.ts"))
            assertEquals(0, origin.count("/hls/vod/seg016.ts"))
        } finally {
            unfilteredSession.close()
        }

        // 过滤: 广告组被移除, 时间轴连续, [48s, 51s) 对应正片 seg016
        val filtered = prepare("/hls/withads.m3u8", HlsPlaybackOptions(filterSegments = true, proxySegments = true))
        val filteredSession = filtered.session()
        try {
            val local = text(httpGet(filtered.data.uri))
            val uris = local.segmentUris()
            assertEquals(32, uris.size)
            assertTrue(local.lineSequence().none { it.contains("/ads/") }, "ad segments must be removed")
            val parsed = local.parseMedia(filtered.data.uri)
            assertEquals(List(32) { 3.0f }, parsed.segments.map { it.duration })

            filteredSession.setPrefetchRange(MediaTimeRange(48_000, 51_000))
            val done = filteredSession.awaitAllDone(1)
            assertEquals(listOf(vodRange(16)), done.map { it.range })
            assertEquals(1, origin.count("/hls/vod/seg016.ts"))
            assertEquals(1, origin.count("/hls/ads/ad000.ts"), "filtered session must not touch ad segments")

            // 过滤后第 16 片对应 seg016, 内容一致
            assertContentEquals(origin.bytesOf("/hls/vod/seg016.ts"), httpGet(uris[16]).body)
        } finally {
            filteredSession.close()
        }
    }

    @Test
    fun `prefetching the whole vod yields a stream ffprobe recognises`() = withFixture {
        val result = prepare("/hls/vod/index.m3u8")
        val session = result.session()
        try {
            val uris = text(httpGet(result.data.uri)).segmentUris()
            session.setPrefetchRange(MediaTimeRange(0, 96_000))
            session.awaitAllDone(32)
            val concatenated = uris.flatMap { httpGet(it).body.toList() }.toByteArray()
            assertValidTs(concatenated, "concatenated vod")
            assertEquals(origin.bytesOfAllVodSegments().size, concatenated.size)
            for (index in 0 until 32) assertEquals(1, origin.count("/hls/vod/seg%03d.ts".format(index)))

            val ffprobe = findExecutable("ffprobe")
            if (ffprobe == null) {
                println("ffprobe not found; skipping media validation")
                return@withFixture
            }
            val file = File.createTempFile("ani-hls-proxy", ".ts")
            try {
                file.writeBytes(concatenated)
                val process = ProcessBuilder(
                    ffprobe, "-v", "error", "-show_entries", "format=duration:stream=codec_name",
                    "-of", "default=noprint_wrappers=1", file.absolutePath,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "ffprobe timed out")
                assertEquals(0, process.exitValue(), "ffprobe failed: $output")
                assertTrue(output.contains("codec_name=h264"), output)
                assertTrue(output.contains("codec_name=aac"), output)
                val duration = output.lineSequence().first { it.startsWith("duration=") }.substringAfter('=').toDouble()
                assertTrue(duration in 94.0..98.0, "unexpected duration $duration: $output")
            } finally {
                file.delete()
            }
        } finally {
            session.close()
        }
    }

    // ---------------- helpers ----------------

    private class ProgressRecorder(val job: Job, private val list: MutableList<List<PrefetchSegmentInfo>>) {
        fun snapshots(): List<List<PrefetchSegmentInfo>> = synchronized(list) { list.toList() }
    }

    private fun CoroutineScope.recordProgress(session: HlsPlaybackProxySession): ProgressRecorder {
        val list = Collections.synchronizedList(mutableListOf<List<PrefetchSegmentInfo>>())
        val started = CountDownLatch(1)
        val job = launch {
            session.prefetchProgress.collect {
                if (it.isNotEmpty()) list += it
                started.countDown()
            }
        }
        started.await(5, TimeUnit.SECONDS)
        return ProgressRecorder(job, list)
    }

    private fun HlsFixtureOrigin.bytesOfAllVodSegments(): ByteArray =
        (0 until 32).flatMap { bytesOf("/hls/vod/seg%03d.ts".format(it)).toList() }.toByteArray()

    private fun findExecutable(name: String): String? {
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        return pathDirs.map { File(it, name) }.firstOrNull { it.canExecute() }?.absolutePath
    }

    @Suppress("unused")
    private fun neverCalled(): Nothing = fail("unreachable")
}
