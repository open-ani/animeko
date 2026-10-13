/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.matcher.ApiVideoResolver
import me.him188.ani.datasources.api.matcher.MediaSourceApiVideoResolverLoader
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.emptySizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiVideoMediaResolverTest {
    private class FakeSource(
        override val mediaSourceId: String,
        private val resolve: suspend (Media) -> WebVideo?,
    ) : MediaSource, ApiVideoResolver {
        override val kind: MediaSourceKind get() = MediaSourceKind.WEB
        override val info: MediaSourceInfo = MediaSourceInfo(mediaSourceId)
        override suspend fun checkConnection(): ConnectionStatus = ConnectionStatus.SUCCESS
        override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = emptySizedSource()
        override suspend fun resolveApiVideo(media: Media): WebVideo? = resolve(media)
    }

    private fun apiVideoMedia(mediaSourceId: String = "json-api-1") = DefaultMedia(
        mediaId = "$mediaSourceId.3252-47463",
        mediaSourceId = mediaSourceId,
        originalUrl = "https://next.example.com/play/47463",
        download = ResourceLocation.WebVideo(
            "https://next.example.com/play/47463",
            apiParameters = mapOf("id" to "47463", "subjectId" to "3252"),
        ),
        originalTitle = "第1集",
        publishedTime = 0,
        properties = MediaProperties(
            subjectName = "葬送的芙莉莲",
            episodeName = "第1集",
            subtitleLanguageIds = listOf("CHS"),
            resolution = "1080P",
            alliance = "线路A",
            size = FileSize.Unspecified,
            subtitleKind = null,
        ),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    private val episode = EpisodeMetadata("第1集", EpisodeSort(1), EpisodeSort(1))

    private fun resolverOf(vararg sources: MediaSource) =
        ApiVideoMediaResolver(MediaSourceApiVideoResolverLoader(flowOf(sources.toList())))

    @Test
    fun `supports only web videos resolved by api`() {
        val resolver = resolverOf()
        assertTrue(resolver.supports(apiVideoMedia()))
        // 没有 apiParameters 的是 WebView 解析的网页, 由平台 web resolver 处理
        assertFalse(
            resolver.supports(
                apiVideoMedia().copy(download = ResourceLocation.WebVideo("https://next.example.com/play/47463")),
            ),
        )
        assertFalse(
            resolver.supports(
                apiVideoMedia().copy(download = ResourceLocation.HttpStreamingFile("https://cdn.example.com/01.mp4")),
            ),
        )
    }

    @Test
    fun `resolve asks the owning source and passes the fresh url and headers to the player`() = runTest {
        var resolvedMedia: Media? = null
        val source = FakeSource("json-api-1") { media ->
            resolvedMedia = media
            WebVideo("https://cdn.example.com/01.mp4?sign=abc", mapOf("Referer" to "https://next.example.com/"))
        }
        val media = apiVideoMedia()

        val provider = resolverOf(FakeSource("other") { null }, source).resolve(media, episode)

        assertEquals(media, resolvedMedia)
        val data = provider as HttpStreamingMediaDataProvider
        assertEquals("https://cdn.example.com/01.mp4?sign=abc", data.uri)
        assertEquals(mapOf("Referer" to "https://next.example.com/"), data.open(backgroundScope).headers)
    }

    @Test
    fun `resolve re-queries the source every time instead of caching an expiring url`() = runTest {
        var calls = 0
        val source = FakeSource("json-api-1") {
            calls++
            WebVideo("https://cdn.example.com/01.mp4?sign=$calls", emptyMap())
        }
        val resolver = resolverOf(source)

        resolver.resolve(apiVideoMedia(), episode)
        val second = resolver.resolve(apiVideoMedia(), episode) as HttpStreamingMediaDataProvider

        assertEquals(2, calls)
        assertEquals("https://cdn.example.com/01.mp4?sign=2", second.uri)
    }

    @Test
    fun `missing source is an engine error`() = runTest {
        val e = assertFailsWith<MediaResolutionException> {
            resolverOf(FakeSource("other") { null }).resolve(apiVideoMedia(), episode)
        }
        assertEquals(ResolutionFailures.ENGINE_ERROR, e.reason)
    }

    @Test
    fun `no video from the site is reported as no matching resource`() = runTest {
        val e = assertFailsWith<MediaResolutionException> {
            resolverOf(FakeSource("json-api-1") { null }).resolve(apiVideoMedia(), episode)
        }
        assertEquals(ResolutionFailures.NO_MATCHING_RESOURCE, e.reason)
    }

    @Test
    fun `io and rate limit failures are network errors`() = runTest {
        val io = assertFailsWith<MediaResolutionException> {
            resolverOf(FakeSource("json-api-1") { throw IOException("boom") }).resolve(apiVideoMedia(), episode)
        }
        assertEquals(ResolutionFailures.NETWORK_ERROR, io.reason)

        val rateLimited = assertFailsWith<MediaResolutionException> {
            resolverOf(FakeSource("json-api-1") { throw RepositoryRateLimitedException() })
                .resolve(apiVideoMedia(), episode)
        }
        assertEquals(ResolutionFailures.NETWORK_ERROR, rateLimited.reason)
    }
}
