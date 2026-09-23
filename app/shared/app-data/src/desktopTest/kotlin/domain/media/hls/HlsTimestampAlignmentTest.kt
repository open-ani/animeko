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
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
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
 * 代理转发分片时把时间戳平移到播放列表时间轴来消除这一跳变. 这里逐片取回代理的输出验证平移结果,
 * 不依赖外部播放器, 因而可以随其他单元测试一起跑.
 */
class HlsTimestampAlignmentTest {
    private fun withProxy(path: String, block: suspend (HlsFixtureOrigin, String) -> Unit) = runBlocking {
        val origin = HlsFixtureOrigin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            val preparer = PlatformHlsPlaybackPreparer(
                provider,
                PlatformHlsPlaybackPreparer.DEFAULT_SEGMENT_CACHE_MAX_BYTES,
                PlatformHlsProxyServerFactory,
            )
            val result = preparer.prepare(
                UriMediaData(origin.url(path)),
                HlsPlaybackOptions(proxySegments = true),
            )
            assertNotNull(result.session, "expected proxy session for $path")
            try {
                block(origin, (result.data as UriMediaData).uri)
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
     * 解复用器看到的时间轴必须一路单调递增, 跨广告边界也不跳.
     *
     * 不直接断言"首个视频 PTS 等于分片起始时间": 锚点取的是分片内最早的时间戳, 而音频通常比视频早
     * (夹具里差 128 毫秒), 平移后视频自然落在起始时间之后. 锚点若改用首个视频 PTS, 音频就会落到
     * 目标起点之前, 对播放列表首片是负值, 取模后变成 2^33 附近的天文数字——正是要修的症状本身.
     */
    @Test
    fun `keeps the timeline monotonic across ad boundaries`() = withProxy("/hls/withads.m3u8") { _, playlistUrl ->
        val entries = parse(httpGet(playlistUrl).body.decodeToString())
        assertTrue(entries.size >= 30, "expected the full fixture, got ${entries.size} segments")

        val timeline = entries.map { entry ->
            val pts = firstPtsMillis(httpGet(entry.url).body)
            assertNotNull(pts, "no pts in ${entry.url}")
            entry.startMillis to pts
        }

        val jumps = mutableListOf<String>()
        for ((index, current) in timeline.withIndex()) {
            val (expectedStart, pts) = current
            val previous = timeline.getOrNull(index - 1) ?: continue
            // 每一步的推进量应当等于播放列表里两片起点之差
            val expectedStep = expectedStart - previous.first
            val actualStep = pts - previous.second
            // 容差取音视频首帧的间距: 锚点是各分片内最早的时间戳, 而最早的是音频还是视频逐片不同,
            // 视频时间轴因此有一帧上下的抖动. 改写一旦失效, 偏差是几万毫秒量级, 照样抓得到.
            if (abs(actualStep - expectedStep) > AUDIO_VIDEO_SKEW_TOLERANCE_MILLIS) {
                jumps += "segment $index: timeline advanced ${actualStep}ms, playlist says ${expectedStep}ms"
            }
        }
        assertEquals(emptyList(), jumps)
    }

    /**
     * 未经改写时广告分片会把时间轴拽回它自己的起点, 这里确认夹具确实是这个形状, 否则上面那条测试
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
    fun `leaves a continuous playlist untouched`() = withProxy("/hls/vod/index.m3u8") { origin, playlistUrl ->
        val entries = parse(httpGet(playlistUrl).body.decodeToString())
        assertTrue(entries.isNotEmpty())
        val served = httpGet(entries.first().url).body
        assertContentEquals(origin.bytesOf("/hls/vod/seg000.ts"), served)
    }

    private fun firstPtsMillis(bytes: ByteArray): Long? =
        TsPacketReader.firstPts(bytes)?.let { TsPacketReader.ticksToMillis(it) }

    private companion object {
        const val AUDIO_VIDEO_SKEW_TOLERANCE_MILLIS = 200L
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size, "size differs")
        val firstDiff = expected.indices.firstOrNull { expected[it] != actual[it] }
        assertEquals(null, firstDiff, "bytes differ at $firstDiff")
    }
}
