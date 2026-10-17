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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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

/**
 * 续播时起播前预缓存的是续播位置处的分片, 位置按去除广告后的时间轴换算.
 *
 * 夹具 `/hls/resume.m3u8` 由现有素材拼成四组: seg000-009 (30 秒), ad000-001 (广告, 6 秒), seg010-019, seg020-031.
 * 去除广告后 40 秒落在 seg013; 原播放列表的 40 秒则是 seg011.
 */
class HlsStartPositionPrefetchTest {
    private fun withPreparer(block: suspend (HlsFixtureOrigin, PlatformHlsPlaybackPreparer) -> Unit) = runBlocking {
        val origin = HlsFixtureOrigin()
        origin.extraBodies["/hls/resume.m3u8"] = resumePlaylist().encodeToByteArray()
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

    private fun resumePlaylist(): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:3")
        fun segment(uri: String) = appendLine("#EXTINF:3.000000,").appendLine(uri)
        (0..9).forEach { segment("vod/seg%03d.ts".format(it)) }
        appendLine("#EXT-X-DISCONTINUITY")
        segment("ads/ad000.ts")
        segment("ads/ad001.ts")
        appendLine("#EXT-X-DISCONTINUITY")
        (10..19).forEach { segment("vod/seg%03d.ts".format(it)) }
        appendLine("#EXT-X-DISCONTINUITY")
        (20..31).forEach { segment("vod/seg%03d.ts".format(it)) }
        appendLine("#EXT-X-ENDLIST")
    }

    private fun String.segmentUris(): List<String> = lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

    /** 等到 [path] 的整片请求多于 [previous] 次. */
    private suspend fun awaitWholeRequest(origin: HlsFixtureOrigin, path: String, previous: Int = 0) {
        withTimeout(5_000) {
            while (origin.countWhole(path) <= previous) delay(10)
        }
    }

    /**
     * 最后一组的探测卡住, 播放列表要等到探测上限才给出. 续播位置之前的组都有了结果, 续播处的分片应在此之前就开始下载,
     * 而不是等播放列表给出、播放器跳转之后.
     */
    @Test
    fun `prefetches the segment at the resume position before the probe finishes`() = withPreparer { origin, preparer ->
        origin.latencyByPath["/hls/vod/seg020.ts"] = 60_000
        val result = coroutineScope {
            val hinted = async {
                preparer.prepare(
                    UriMediaData(origin.url("/hls/resume.m3u8")),
                    HlsPlaybackOptions(filterSegments = true),
                    startPositionHintMillis = 40_000,
                )
            }
            awaitWholeRequest(origin, "/hls/vod/seg013.ts")
            awaitWholeRequest(origin, "/hls/vod/seg014.ts")
            assertFalse(hinted.isCompleted, "the resume segment must be requested while the probe is still running")
            hinted.await()
        }
        try {
            val local = httpGet(result.data.uri).body.decodeToString()
            assertFalse(local.contains("/ads/"), "ad group must be removed")
            val segmentUris = local.segmentUris()
            assertEquals(32, segmentUris.size)
            assertContentEquals(origin.bytesOf("/hls/vod/seg013.ts"), httpGet(segmentUris[13]).body)
            assertEquals(1, origin.countWhole("/hls/vod/seg013.ts"), "player request must be served from the prefetch")
            assertEquals(0, origin.countWhole("/hls/vod/seg011.ts"), "position must be mapped on the ad-free timeline")
            // 播放器打开时先读首片, 续播的跳转在开始播放之后, 所以续播也要预缓存首片
            assertEquals(1, origin.countWhole("/hls/vod/seg000.ts"), "first segment must be prefetched when resuming too")
        } finally {
            result.session?.close()
        }
    }

    @Test
    fun `a failed prefetch at the resume position leaves playback intact`() = withPreparer { origin, preparer ->
        val reference = preparer.prepare(UriMediaData(origin.url("/hls/withads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        val expected = try {
            httpGet(reference.data.uri).body.decodeToString().segmentUris()[20].let { httpGet(it).body }
        } finally {
            reference.session?.close()
        }

        // withads.m3u8 去除广告后的 60 秒是 seg020
        val referenceRequests = origin.countWhole("/hls/vod/seg020.ts")
        origin.failPaths["/hls/vod/seg020.ts"] = 503
        val result = preparer.prepare(
            UriMediaData(origin.url("/hls/withads.m3u8")),
            HlsPlaybackOptions(filterSegments = true),
            startPositionHintMillis = 60_000,
        )
        val session = assertNotNull(result.session)
        try {
            val segmentUris = httpGet(result.data.uri).body.decodeToString().segmentUris()
            assertEquals(32, segmentUris.size)
            awaitWholeRequest(origin, "/hls/vod/seg020.ts", previous = referenceRequests)
            origin.failPaths.clear()

            val served = httpGet(segmentUris[20])
            assertEquals(200, served.status)
            assertContentEquals(expected, served.body)
        } finally {
            session.close()
        }
    }

    @Test
    fun `a hint beyond the end falls back to prefetching the first segment`() = withPreparer { origin, preparer ->
        val result = preparer.prepare(
            UriMediaData(origin.url("/hls/withads.m3u8")),
            HlsPlaybackOptions(filterSegments = true),
            startPositionHintMillis = 3_600_000,
        )
        val session = assertNotNull(result.session)
        try {
            val local = httpGet(result.data.uri).body.decodeToString()
            assertFalse(local.contains("/ads/"))
            assertEquals(32, local.segmentUris().size)
            awaitWholeRequest(origin, "/hls/vod/seg000.ts")
            assertTrue(httpGet(local.segmentUris().first()).body.isNotEmpty())
            assertEquals(1, origin.countWhole("/hls/vod/seg000.ts"))
        } finally {
            session.close()
        }
    }
}
