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
import me.him188.ani.app.domain.media.player.prefetch.MediaTimeRange
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 拼接流里广告是独立转码的, 其 PTS 自成一条时间轴 (夹具中的 `ads/ad000.ts` 是 `vod/seg000.ts` 的副本,
 * PTS 从 1.528 秒起, 却位于播放列表的 48 秒处). libavformat 的 HLS 实现不按 `#EXT-X-DISCONTINUITY`
 * 重映射时间戳, mpv 的 `time-pos` 直接取解复用器的 PTS, 桌面端的播放位置因此会在广告处跳到别处.
 *
 * 代理转发分片时把各组的时间戳接到首组的时间轴上来消除这一跳变. 这里逐片取回代理的输出验证平移结果,
 * 不依赖外部播放器, 因而可以随其他单元测试一起跑.
 */
class HlsTimestampAlignmentTest {
    private fun withProxy(
        path: String,
        setup: (HlsFixtureOrigin) -> Unit = {},
        block: suspend (HlsFixtureOrigin, HlsPlaybackPreparerResult) -> Unit,
    ) = runBlocking {
        val origin = HlsFixtureOrigin()
        setup(origin)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            val preparer = PlatformHlsPlaybackPreparer(
                provider,
                PlatformHlsPlaybackPreparer.DEFAULT_SEGMENT_CACHE_MAX_BYTES,
                PlatformHlsProxyServerFactory,
                alignTimestamps = true,
            )
            val result = preparer.prepare(
                UriMediaData(origin.url(path)),
                HlsPlaybackOptions(proxySegments = true),
            )
            assertNotNull(result.session, "expected proxy session for $path")
            try {
                block(origin, result)
            } finally {
                result.session?.close()
            }
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private class Entry(val startMillis: Long, val url: String)

    /** 按 `#EXTINF` 累加算出每个分片应处的位置. */
    private fun parse(playlist: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var cursor = 0L
        var pending: Long? = null
        for (raw in playlist.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:") ->
                    pending = ((line.removePrefix("#EXTINF:").substringBefore(',').toDouble()) * 1000).toLong()

                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    entries += Entry(cursor, line)
                    cursor += pending ?: 0L
                    pending = null
                }
            }
        }
        return entries
    }

    /**
     * 每一片首个视频 PTS 的推进量都应当等于播放列表里两片起点之差: 组内是源站原样的时间戳, 夹具每片恰好 3 秒;
     * 拼接点上是对齐的结果. 只留毫秒取整的余量. 逐片对齐到 `#EXTINF` 的做法在这里会差出 128 毫秒
     * (首片最早的时间戳是音频, 其余片是视频).
     */
    private fun assertTimelineFollowsPlaylist(playlistUrl: String) {
        val entries = parse(httpGet(playlistUrl).body.decodeToString())
        val timeline = entries.map { entry ->
            val pts = firstPtsMillis(httpGet(entry.url).body)
            assertNotNull(pts, "no pts in ${entry.url}")
            entry.startMillis to pts
        }
        val jumps = mutableListOf<String>()
        for ((index, current) in timeline.withIndex()) {
            val (expectedStart, pts) = current
            val previous = timeline.getOrNull(index - 1) ?: continue
            val expectedStep = expectedStart - previous.first
            val actualStep = pts - previous.second
            if (abs(actualStep - expectedStep) > ROUNDING_TOLERANCE_MILLIS) {
                jumps += "segment $index: timeline advanced ${actualStep}ms, playlist says ${expectedStep}ms"
            }
        }
        assertEquals(emptyList(), jumps)
    }

    @Test
    fun `keeps the timeline continuous across ad boundaries`() = withProxy("/hls/withads.m3u8") { _, result ->
        val entries = parse(httpGet(result.data.uri).body.decodeToString())
        assertTrue(entries.size >= 30, "expected the full fixture, got ${entries.size} segments")
        assertTimelineFollowsPlaylist(result.data.uri)
    }

    /**
     * 同一段广告插在两处, 两处要平移的量不同. 预缓存按地址缓存分片, 缓存改写后的字节的话,
     * 另一处命中缓存就会带着前一处的时间轴发出.
     */
    @Test
    fun `aligns a repeated ad at each place even when one of them was prefetched`() = withProxy(
        "/hls/repeated-ads.m3u8",
        setup = { it.extraBodies["/hls/repeated-ads.m3u8"] = REPEATED_ADS_PLAYLIST.encodeToByteArray() },
    ) { origin, result ->
        // 第一处广告位于 [24s, 30s)
        assertNotNull(result.session).setPrefetchRange(MediaTimeRange(24_000, 27_000))
        withTimeout(5_000) {
            while (origin.countWhole("/hls/ads/ad000.ts") == 0) delay(10)
        }
        assertTimelineFollowsPlaylist(result.data.uri)
        assertEquals(1, origin.countWhole("/hls/ads/ad000.ts"), "both places must be served from the prefetched bytes")
    }

    /**
     * 未经改写时广告分片会把时间轴拽回它自己的起点, 这里确认夹具确实是这个形状, 否则上面的测试
     * 即使改写失效也会通过.
     */
    @Test
    fun `fixture ads really do carry a rewound timeline`() {
        HlsFixtureOrigin().use { origin ->
            val firstAd = firstPtsMillis(origin.bytesOf("/hls/ads/ad000.ts"))
            val mainAtSamePosition = firstPtsMillis(origin.bytesOf("/hls/vod/seg016.ts"))
            assertNotNull(firstAd)
            assertNotNull(mainAtSamePosition)
            // 广告位于播放列表 48 秒处, 其原始 PTS 却在 2 秒以内
            assertTrue(firstAd < 2_000, "ad000 pts=$firstAd")
            assertTrue(mainAtSamePosition > 40_000, "seg016 pts=$mainAtSamePosition")
        }
    }

    /**
     * 没有 `#EXT-X-DISCONTINUITY` 的播放列表本来就是一条时间轴, 代理不该改动分片字节.
     */
    @Test
    fun `leaves a continuous playlist untouched`() = withProxy("/hls/vod/index.m3u8") { origin, result ->
        val entries = parse(httpGet(result.data.uri).body.decodeToString())
        assertTrue(entries.isNotEmpty())
        val served = httpGet(entries.first().url).body
        assertContentEquals(origin.bytesOf("/hls/vod/seg000.ts"), served)
    }

    private fun firstPtsMillis(bytes: ByteArray): Long? =
        TsPacketReader.firstPts(bytes)?.let { TsPacketReader.ticksToMillis(it) }

    private companion object {
        const val ROUNDING_TOLERANCE_MILLIS = 1L

        /** 正片 24 秒, 广告 6 秒, 正片 24 秒, 同一段广告 6 秒, 正片 24 秒. */
        val REPEATED_ADS_PLAYLIST = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:3")
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")
            fun segment(path: String) {
                appendLine("#EXTINF:3.000000,")
                appendLine(path)
            }

            fun ad() {
                appendLine("#EXT-X-DISCONTINUITY")
                segment("ads/ad000.ts")
                segment("ads/ad001.ts")
                appendLine("#EXT-X-DISCONTINUITY")
            }
            (0..7).forEach { segment("vod/seg%03d.ts".format(it)) }
            ad()
            (8..15).forEach { segment("vod/seg%03d.ts".format(it)) }
            ad()
            (16..23).forEach { segment("vod/seg%03d.ts".format(it)) }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size, "size differs")
        val firstDiff = expected.indices.firstOrNull { expected[it] != actual[it] }
        assertEquals(null, firstDiff, "bytes differ at $firstDiff")
    }
}
