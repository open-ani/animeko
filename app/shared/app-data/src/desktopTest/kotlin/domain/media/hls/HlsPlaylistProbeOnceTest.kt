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
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 同一个会话里每个远端媒体播放列表只探测一次: 播放器重复请求时拿到的必须是同一份列表, 否则负载下的第二次探测
 * 可能漏掉广告, 交给播放器分片不同的列表.
 *
 * 夹具 `withads.m3u8` 分三组: seg000-015, ad000-001, seg016-031. 未过滤时 34 个分片, 过滤后 32 个.
 */
class HlsPlaylistProbeOnceTest {
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

    private fun HlsFixtureOrigin.countProbes(path: String): Int = count(path) - countWhole(path)

    /** 以 `withads.m3u8` 为唯一变体的主播放列表, 返回播放器拿到的变体地址. */
    private suspend fun HlsFixtureOrigin.prepareVariant(preparer: PlatformHlsPlaybackPreparer): Pair<HlsPlaybackPreparerResult, String> {
        extraBodies["/hls/master-withads.m3u8"] = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=30000\nwithads.m3u8\n".encodeToByteArray()
        val result = preparer.prepare(UriMediaData(url("/hls/master-withads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        assertNotNull(result.session)
        val variantUri = httpGet(result.data.uri).body.decodeToString().segmentUris().single()
        return result to variantUri
    }

    @Test
    fun `repeated variant requests get the first result without probing again`() = withPreparer { origin, preparer ->
        val (result, variantUri) = origin.prepareVariant(preparer)
        try {
            val first = httpGet(variantUri).body.decodeToString()
            assertEquals(32, first.segmentUris().size)

            // 再探测的话广告组探测不到, 会原样保留
            origin.latencyByPath["/hls/ads/ad000.ts"] = 60_000
            val second = httpGet(variantUri).body.decodeToString()

            assertEquals(first, second)
            assertEquals(1, origin.count("/hls/withads.m3u8"))
            assertEquals(1, origin.countProbes("/hls/ads/ad000.ts"))
        } finally {
            result.session?.close()
        }
    }

    @Test
    fun `concurrent first variant requests share one probe`() = withPreparer { origin, preparer ->
        val (result, variantUri) = origin.prepareVariant(preparer)
        try {
            // 让两个请求都在处理完成前到达
            origin.latencyByPath["/hls/withads.m3u8"] = 500
            val (a, b) = coroutineScope {
                val a = async(Dispatchers.IO) { httpGet(variantUri).body.decodeToString() }
                val b = async(Dispatchers.IO) { httpGet(variantUri).body.decodeToString() }
                a.await() to b.await()
            }

            assertEquals(a, b)
            assertEquals(32, a.segmentUris().size)
            assertEquals(1, origin.count("/hls/withads.m3u8"))
            assertEquals(1, origin.countProbes("/hls/ads/ad000.ts"))
        } finally {
            result.session?.close()
        }
    }

    @Test
    fun `groups sharing a first segment are probed once and all classified`() = withPreparer { origin, preparer ->
        // 同一段广告插入两处
        val adBreak = "#EXT-X-DISCONTINUITY\n#EXTINF:3.000000,\nads/ad000.ts\n#EXTINF:3.000000,\nads/ad001.ts\n"
        val withAds = origin.bytesOf("/hls/withads.m3u8").decodeToString()
        val anchor = "vod/seg024.ts\n"
        check(anchor in withAds)
        origin.extraBodies["/hls/twoads.m3u8"] = withAds
            .replace("#EXTINF:3.000000,\n$anchor", "${adBreak}#EXT-X-DISCONTINUITY\n#EXTINF:3.000000,\n$anchor")
            .encodeToByteArray()

        val result = preparer.prepare(UriMediaData(origin.url("/hls/twoads.m3u8")), HlsPlaybackOptions(filterSegments = true))
        try {
            assertNotNull(result.session)
            val local = httpGet(result.data.uri).body.decodeToString()
            assertEquals(32, local.segmentUris().size, "both ad breaks must be removed")
            assertEquals(1, origin.countProbes("/hls/ads/ad000.ts"))
        } finally {
            result.session?.close()
        }
    }
}
