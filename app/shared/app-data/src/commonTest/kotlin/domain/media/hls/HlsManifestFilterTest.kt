/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.hls.HlsPtsContinuity.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsManifestFilterTest {
    /**
     * 探测结果按 [HlsManifestAnalysis.probeTargets] 的顺序对应到组. 这里的广告组在中间,
     * 对应关系错位时会删掉正片组.
     */
    @Test
    fun `removes the group whose pts does not continue the main timeline`() = runTest {
        val analysis = HlsManifestFilter.analyze(
            mediaPlaylist(
                group(30, duration = 3.0, uriPrefix = "main/a"),
                group(3, duration = 6.0, uriPrefix = "x/ins"),
                group(30, duration = 3.0, uriPrefix = "main/b"),
            ),
        )

        val result = HlsManifestFilter.filter(analysis) { targets ->
            assertEquals(listOf(0, 1, 2), targets.map { it.groupIndex })
            listOf(1_400L, 1_466L, 91_400L)
        }

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertEquals(listOf(1), result.removedGroups.map { it.index })
        assertFalse("x/ins0.ts" in result.content)
        assertEquals(60, result.content.mediaSegmentUriCount())
    }

    /**
     * 一段插播被额外的 `#EXT-X-DISCONTINUITY` 切成两组时两组都删, 删后前后两段正片之间只留一个拼接标记.
     */
    @Test
    fun `removes an ad break split into two groups`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-DISCONTINUITY-SEQUENCE:7")
            appendLine("#EXT-X-TARGETDURATION:10")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/a"),
                group(2, duration = 7.0, uriPrefix = "x/ins"),
                group(1, duration = 5.0, uriPrefix = "x/ins", startNumber = 2),
                group(30, duration = 3.0, uriPrefix = "main/b"),
            )
            append("#EXT-X-ENDLIST")
        }

        val result = HlsManifestFilter.decide(
            HlsManifestFilter.analyze(content),
            Verdict(ads = setOf(1, 2), main = setOf(0, 3)),
        )

        assertEquals(listOf(1, 2), result.removedGroups.map { it.index })
        assertTrue("#EXT-X-DISCONTINUITY-SEQUENCE:7" in result.content)
        assertEquals(1, result.content.lines().count { it == "#EXT-X-DISCONTINUITY" })
        assertEquals(60, result.content.mediaSegmentUriCount())
    }

    /**
     * 正片也可能分几段独立编码, 同样自成时间轴. 一段连续的链外组超过上限就不删, 哪怕它由几组拼成.
     */
    @Test
    fun `keeps an off-chain run longer than an ad break`() {
        val content = mediaPlaylist(
            group(30, duration = 3.0, uriPrefix = "main/a"),
            group(8, duration = 4.0, uriPrefix = "op/part"),
            group(8, duration = 4.0, uriPrefix = "op/part", startNumber = 8),
            group(30, duration = 3.0, uriPrefix = "main/b"),
        )

        val result = HlsManifestFilter.decide(
            HlsManifestFilter.analyze(content),
            Verdict(ads = setOf(1, 2), main = setOf(0, 3)),
        )

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("ad_break_too_long", result.reason)
        assertEquals(listOf(1, 2), result.oversizedGroups)
        assertEquals(content, result.content)
    }

    /**
     * 续播位置在去广告后的时间轴上: 广告之后的位置要扣掉广告时长, 下一片跳过广告.
     * 广告组还没有探测结果时按保留算, 位置落在它里面.
     */
    @Test
    fun `locates a position on the ad-free timeline`() {
        val analysis = HlsManifestFilter.analyze(
            mediaPlaylist(
                group(30, duration = 3.0, uriPrefix = "main/a"),
                group(3, duration = 6.0, uriPrefix = "x/ins"),
                group(30, duration = 3.0, uriPrefix = "main/b"),
            ),
        )
        fun locate(firstPts: List<Long?>, positionMillis: Long) = HlsManifestFilter.locate(
            analysis,
            HlsManifestFilter.classify(analysis, firstPts),
            positionMillis,
            count = 2,
        )?.segmentUris?.map { it.substringAfter("127.0.0.1/") }

        val probed = listOf(1_400L, 1_466L, 91_400L)
        assertEquals(listOf("main/b0.ts", "main/b1.ts"), locate(probed, 91_000))
        assertEquals(listOf("main/a29.ts", "main/b0.ts"), locate(probed, 89_000))
        assertEquals(null, locate(probed, 180_000))

        assertEquals(listOf("x/ins0.ts", "x/ins1.ts"), locate(listOf(1_400L, null, null), 91_000))
    }

    @Test
    fun `keeps playlist unchanged without pts evidence`() {
        val content = mediaPlaylist(
            group(30, duration = 3.0, uriPrefix = "main/a"),
            group(3, duration = 6.0, uriPrefix = "x/ins"),
            group(30, duration = 3.0, uriPrefix = "main/b"),
        )

        val result = HlsManifestFilter.decide(HlsManifestFilter.analyze(content), Verdict.Inconclusive)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals(content, result.content)
    }

    /**
     * 探测读到的是密文, 解出的时间戳不可信. 即使 IV 显式给出、删分片本身没问题, 也不过滤.
     */
    @Test
    fun `does not filter encrypted playlist`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"enc.key\",IV=0x00000000000000000000000000000000")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/a"),
                group(3, duration = 6.0, uriPrefix = "x/ins"),
                group(30, duration = 3.0, uriPrefix = "main/b"),
            )
            append("#EXT-X-ENDLIST")
        }

        val analysis = HlsManifestFilter.analyze(content)

        assertTrue(analysis.isConclusive)
        assertEquals("encrypted", HlsManifestFilter.decide(analysis, Verdict(setOf(1), setOf(0, 2))).reason)
    }

    @Test
    fun `does not filter byte range playlist with implicit offset`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:4")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-BYTERANGE:3000")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/a"),
                group(3, duration = 6.0, uriPrefix = "x/ins"),
                group(30, duration = 3.0, uriPrefix = "main/b"),
            )
            append("#EXT-X-ENDLIST")
        }

        val analysis = HlsManifestFilter.analyze(content)

        assertTrue(analysis.isConclusive)
        assertEquals("byterange_implicit_offset", HlsManifestFilter.decide(analysis, Verdict(setOf(1), setOf(0, 2))).reason)
    }

    @Test
    fun `needs no probing for playlists that cannot contain spliced ads`() {
        val noDiscontinuity = mediaPlaylist(group(4, duration = 10.0, uriPrefix = "main/a"), discontinuity = false)
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000
            low/index.m3u8
        """.trimIndent()
        val live = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10,
            seg0.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10,
            seg1.ts
        """.trimIndent()

        for (content in listOf(noDiscontinuity, master, live)) {
            val analysis = HlsManifestFilter.analyze(content)
            assertTrue(analysis.isConclusive)
            assertTrue(analysis.probeTargets.isEmpty())
        }
        assertTrue(HlsManifestFilter.analyze(master).isMasterPlaylist)
    }
}

private fun mediaPlaylist(
    vararg groups: List<TestSegment>,
    discontinuity: Boolean = true,
): String {
    return buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:15")
        mediaPlaylistBody(*groups, discontinuity = discontinuity)
        append("#EXT-X-ENDLIST")
    }
}

private fun StringBuilder.mediaPlaylistBody(
    vararg groups: List<TestSegment>,
    discontinuity: Boolean = true,
) {
    groups.forEachIndexed { groupIndex, group ->
        if (discontinuity && groupIndex > 0) {
            appendLine("#EXT-X-DISCONTINUITY")
        }
        group.forEach { segment ->
            appendLine("#EXTINF:${segment.duration},")
            appendLine(segment.uri)
        }
    }
}

private fun group(
    count: Int,
    duration: Double,
    uriPrefix: String,
    startNumber: Int = 0,
): List<TestSegment> {
    return List(count) { index ->
        TestSegment(
            duration = duration,
            uri = "$uriPrefix${startNumber + index}.ts",
        )
    }
}

private data class TestSegment(
    val duration: Double,
    val uri: String,
)

private fun String.mediaSegmentUriCount(): Int {
    return lines().count { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
    }
}
