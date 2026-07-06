/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.resolver

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.emptySizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.tools.datasourcetestmcp.source.TestSubjectEpisodeSourceInput
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbeResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoUrlProbeEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelTestExecutorTest {
    @Test
    fun execute_allChannels_tests_every_candidate() = runTest {
        val resolver = FakeResolver(
            mapOf(
                "c1" to ResolveResult(
                    resolvedVideo = resolved("https://video.example.com/c1.m3u8"),
                    diagnostics = buildJsonObject { put("candidate", "c1") },
                ),
                "c2" to ResolveResult(
                    resolvedVideo = resolved("https://video.example.com/c2.m3u8"),
                    diagnostics = buildJsonObject { put("candidate", "c2") },
                ),
                "c3" to ResolveResult(
                    diagnostics = buildJsonObject { put("candidate", "c3") },
                    errors = listOf("No video URL matched"),
                ),
            ),
        )
        val probe = FakeProbe(
            mapOf(
                "https://video.example.com/c1.m3u8" to probeResult(
                    url = "https://video.example.com/c1.m3u8",
                    ok = false,
                    summary = "Probe failed",
                ),
                "https://video.example.com/c2.m3u8" to probeResult(
                    url = "https://video.example.com/c2.m3u8",
                    ok = true,
                    summary = "Probe succeeded",
                ),
            ),
        )

        val source = FakeWebSource("source")
        val results = ChannelTestExecutor(resolver, probe).execute(
            playableCandidates = listOf(candidate("c1"), candidate("c2"), candidate("c3")),
            matchersFor = { listOf(source.matcher) },
            probeTimeoutMillis = 5_000,
            candidateTestMode = CandidateTestMode.ALL_CHANNELS,
        )

        assertEquals(listOf("c1", "c2", "c3"), resolver.calls)
        assertEquals(
            listOf("https://video.example.com/c1.m3u8", "https://video.example.com/c2.m3u8"),
            probe.calls,
        )
        assertEquals(3, results.size)
        assertEquals(false, results[0].ok)
        assertEquals(true, results[1].ok)
        assertEquals("failed", results[2].resolveStatus)
        assertEquals("not_run", results[2].probeStatus)
    }

    @Test
    fun execute_firstSuccess_stops_after_first_playable_candidate() = runTest {
        val resolver = FakeResolver(
            mapOf(
                "c1" to ResolveResult(
                    resolvedVideo = resolved("https://video.example.com/c1.m3u8"),
                ),
                "c2" to ResolveResult(
                    resolvedVideo = resolved("https://video.example.com/c2.m3u8"),
                ),
                "c3" to ResolveResult(
                    resolvedVideo = resolved("https://video.example.com/c3.m3u8"),
                ),
            ),
        )
        val probe = FakeProbe(
            mapOf(
                "https://video.example.com/c1.m3u8" to probeResult(
                    url = "https://video.example.com/c1.m3u8",
                    ok = false,
                    summary = "Probe failed",
                ),
                "https://video.example.com/c2.m3u8" to probeResult(
                    url = "https://video.example.com/c2.m3u8",
                    ok = true,
                    summary = "Probe succeeded",
                ),
            ),
        )

        val source = FakeWebSource("source")
        val results = ChannelTestExecutor(resolver, probe).execute(
            playableCandidates = listOf(candidate("c1"), candidate("c2"), candidate("c3")),
            matchersFor = { listOf(source.matcher) },
            probeTimeoutMillis = 5_000,
            candidateTestMode = CandidateTestMode.FIRST_SUCCESS,
        )

        assertEquals(listOf("c1", "c2"), resolver.calls)
        assertEquals(
            listOf("https://video.example.com/c1.m3u8", "https://video.example.com/c2.m3u8"),
            probe.calls,
        )
        assertEquals(2, results.size)
        assertEquals(true, results.last().ok)
    }

    @Test
    fun subjectEpisodeInput_defaults_to_allChannels() {
        val input = TestSubjectEpisodeSourceInput(
            subjectId = 1,
            episodeId = 1,
        )

        assertEquals(CandidateTestMode.ALL_CHANNELS, input.candidateTestMode)
    }

    private fun candidate(mediaId: String): MediaMatch {
        return MediaMatch(
            media = DefaultMedia(
                mediaId = mediaId,
                mediaSourceId = "source",
                originalUrl = "https://example.com/$mediaId",
                download = ResourceLocation.WebVideo("https://example.com/$mediaId"),
                originalTitle = mediaId,
                publishedTime = 0L,
                properties = MediaProperties(
                    subjectName = mediaId,
                    episodeName = mediaId,
                    subtitleLanguageIds = listOf("CHS"),
                    resolution = "1080P",
                    alliance = "source",
                    size = FileSize.Unspecified,
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
                episodeRange = null,
                location = MediaSourceLocation.Online,
                kind = MediaSourceKind.WEB,
            ),
            kind = MatchKind.FUZZY,
        )
    }

    private fun resolved(url: String): ResolvedVideoResult {
        return ResolvedVideoResult(
            url = url,
            headers = mapOf("User-Agent" to "test"),
            strategy = "test",
        )
    }

    private fun probeResult(
        url: String,
        ok: Boolean,
        summary: String,
    ): VideoProbeResult {
        return VideoProbeResult(
            ok = ok,
            url = url,
            kind = "m3u8",
            summary = summary,
            errors = if (ok) emptyList() else listOf(summary),
        )
    }
}

private class FakeResolver(
    private val results: Map<String, ResolveResult>,
) : CandidateVideoResolver {
    val calls = mutableListOf<String>()

    override suspend fun resolve(media: Media, matchers: List<WebVideoMatcher>): ResolveResult {
        calls += media.mediaId
        return results.getValue(media.mediaId)
    }

    override suspend fun resolvePage(
        media: Media,
        pageUrl: String,
        matchers: List<WebVideoMatcher>,
    ): ResolveResult {
        error("Unused in test")
    }
}

private class FakeProbe(
    private val results: Map<String, VideoProbeResult>,
) : VideoUrlProbeEngine {
    val calls = mutableListOf<String>()

    override suspend fun probe(url: String, headers: Map<String, String>): VideoProbeResult {
        calls += url
        return results.getValue(url)
    }
}

private class FakeWebSource(
    override val mediaSourceId: String,
) : MediaSource, WebVideoMatcherProvider {
    override val kind: MediaSourceKind = MediaSourceKind.WEB
    override val info: MediaSourceInfo = MediaSourceInfo("test")
    override val matcher: WebVideoMatcher = WebVideoMatcher { url, _ ->
        WebVideoMatcher.MatchResult.Matched(WebVideo(url, emptyMap()))
    }

    override suspend fun checkConnection(): ConnectionStatus = ConnectionStatus.SUCCESS

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = emptySizedSource()
}
