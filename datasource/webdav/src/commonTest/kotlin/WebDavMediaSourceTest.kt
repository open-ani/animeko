/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebDavMediaSourceTest {
    @Test
    fun `file numbers distinguish episode fractions resolution labels and specials`() = runTest {
        HttpClient(MockEngine {
            xmlResponse(multistatus(
                entry("Anime.S01E02.1080p.mp4"),
                entry("Anime.S01E02.5.mkv"),
                entry("Anime.S00E01.mkv"),
            ))
        }).use { client ->
            val episodes = source(client).browseSubject(BrowseSubject("Anime", "https://nas.test/dav/")).single().episodes
                .associate { it.name to it.episodeSort }
            assertEquals(EpisodeSort(2), episodes["Anime.S01E02.1080p.mp4"])
            assertEquals(EpisodeSort("2.5"), episodes["Anime.S01E02.5.mkv"])
            assertEquals(EpisodeSort("SP1"), episodes["Anime.S00E01.mkv"])
        }
    }

    @Test
    fun `recursive search caches directories and returns every episode`() = runTest {
        var requests = 0
        HttpClient(MockEngine { request ->
            requests++
            assertEquals("PROPFIND", request.method.value)
            assertEquals("1", request.headers["Depth"])
            assertEquals("Basic dXNlcjo=", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/dav/" -> xmlResponse(multistatus(entry("/dav/", directory = true), entry("/dav/Anime/", directory = true)))
                "/dav/Anime/" -> xmlResponse(multistatus(
                    entry("/dav/Anime/", directory = true),
                    entry("01.mkv", size = 1024),
                    entry("Anime.S01E02.1080p.mp4", size = 2048),
                    entry("cover.jpg"),
                ))
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }).use { client ->
            val source = source(client)
            assertTrue(source.supportsBrowsing)
            val subject = source.searchSubjects("Anime").single()
            assertEquals("Anime", subject.name)
            assertEquals("https://nas.test/dav/Anime/", subject.url)
            assertEquals(listOf(subject), source.searchSubjects("Anime"))
            assertEquals(2, requests)
            val results = source.fetch(query()).results.toList()
            assertEquals(2, results.size)
            assertEquals(listOf(EpisodeRange.single(EpisodeSort(1)), EpisodeRange.single(EpisodeSort(2))), results.map { it.media.episodeRange })
            assertEquals(1024L, results.first().media.properties.size.inBytes)
            assertEquals(2, requests)
            val channel = source.browseSubject(subject).single()
            assertEquals(2, channel.episodes.size)
            val automatic = results.first().media
            val manual = source.createMedia(subject, null, channel.episodes.first(), EpisodeSort(1))
            assertEquals(automatic.mediaId, manual.mediaId)
            val location = assertIs<ResourceLocation.WebVideo>(manual.download)
            assertTrue(location.isResolvedByApi)
            assertFalse(location.uri.contains("user"))
            val video = source.resolveApiVideo(manual)!!
            assertEquals("https://nas.test/dav/Anime/01.mkv", video.m3u8Url)
            assertEquals("Basic dXNlcjo=", video.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `multistatus accepts namespace variants and encoded relative and absolute hrefs`() {
        HttpClient(MockEngine { error("No HTTP requests expected") }).use { client ->
            val dav = WebDavClient("https://nas.test/dav/", "", "", client.asScopedHttpClient())
            val entries = dav.parseMultistatus(
                multistatus(
                    entry("/dav/", directory = true),
                    entry("https://nas.test/dav/%E5%8A%A8%E7%94%BB/", directory = true),
                    entry("Episode%20%231%2B2.mkv", size = 99),
                ).replace("d:", "D:"),
                dav.root,
            )
            assertEquals(listOf("动画", "Episode #1+2.mkv"), entries.map { it.name })
            assertEquals("https://nas.test/dav/Episode%20%231+2.mkv", entries.last().url)
            assertEquals(99L, entries.last().size)
            assertTrue(entries.first().isDirectory)
        }
    }

    @Test
    fun `failed properties outside roots and foreign origins are ignored`() {
        HttpClient(MockEngine { error("No HTTP requests expected") }).use { client ->
            val dav = WebDavClient("https://nas.test/dav/", "", "", client.asScopedHttpClient())
            val entries = dav.parseMultistatus(multistatus(
                entry("https://foreign.test/dav/stolen.mkv"),
                entry("/outside.mkv"),
                entry("../outside.mkv"),
                entry("%2e%2e/outside.mkv"),
                entry("/dav/nested/not-a-direct-child.mkv"),
                entry("missing.mkv").replace("200 OK", "404 Not Found"),
                entry("valid.mkv"),
            ), dav.root)
            assertEquals(listOf("valid.mkv"), entries.map { it.name })
        }
    }

    @Test
    fun `anonymous requests omit authorization and invalid responses fail connection`() = runTest {
        HttpClient(MockEngine { request ->
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            respond("<html>Login required</html>")
        }).use { client ->
            assertEquals(ConnectionStatus.FAILED, source(client, username = "").checkConnection())
        }
        HttpClient(MockEngine { respond("Denied", HttpStatusCode.Unauthorized) }).use { client ->
            assertEquals(ConnectionStatus.FAILED, source(client).checkConnection())
        }
    }

    @Test
    fun `connection check propagates cancellation`() = runTest {
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use { client ->
            assertFailsWith<CancellationException> { source(client).checkConnection() }
        }
    }

    @Test
    fun `browser selection cannot resolve credentials for another server or directory`() = runTest {
        HttpClient(MockEngine { error("No HTTP requests expected") }).use { client ->
            val source = source(client)
            val subject = BrowseSubject("Anime", "https://nas.test/dav/Anime/")
            for (url in listOf("https://foreign.test/dav/video.mkv", "https://nas.test/outside.mkv", "https://nas.test/dav/%2e%2e/video.mkv")) {
                assertFailsWith<IllegalArgumentException> {
                    source.createMedia(subject, null, BrowseEpisode("Video", url), EpisodeSort(1))
                }
            }
            val first = source.createMedia(subject, null, BrowseEpisode("Video", subject.url + "01.mkv"), EpisodeSort(1))
            val other = source(client, id = "other")
            val second = other.createMedia(subject, null, BrowseEpisode("Video", subject.url + "01.mkv"), EpisodeSort(1))
            assertNotEquals(first.mediaId, second.mediaId)
            assertEquals(null, other.resolveApiVideo(first))
        }
    }

    @Test
    fun `root configuration validates schemes and allows anonymous empty credentials`() {
        val parameters = WebDavMediaSource.Parameters
        assertTrue(parameters.baseUrl.validate("https://nas.test/dav/动画"))
        assertTrue(parameters.baseUrl.validate("http://[::1]:8080/dav/"))
        for (url in listOf("", "ftp://nas.test/dav/", "https://user:password@nas.test/dav/", "https://nas.test/dav/?token=secret")) {
            assertFalse(parameters.baseUrl.validate(url))
        }
        assertTrue(parameters.username.validate(""))
        assertTrue(parameters.password.validate(""))
    }

    @Test
    fun `private network WebDAV is marked as LAN`() {
        HttpClient(MockEngine { error("No HTTP requests expected") }).use { client ->
            val localSource = source(client, baseUrl = "http://192.168.1.20/dav/")
            assertEquals(MediaSourceLocation.Lan, localSource.location)
            val media = localSource.createMedia(
                BrowseSubject("Anime", "http://192.168.1.20/dav/Anime/"),
                null,
                BrowseEpisode("Anime.S01E01.mkv", "http://192.168.1.20/dav/Anime/Anime.S01E01.mkv"),
                EpisodeSort(1),
            )
            assertEquals(MediaSourceLocation.Lan, media.location)
            assertEquals(MediaSourceLocation.Online, source(client).location)
        }
    }

    private fun source(
        client: HttpClient,
        id: String = "nas",
        username: String = "user",
        baseUrl: String = "https://nas.test/dav/",
    ) = WebDavMediaSource(
        id,
        MediaSourceConfig(arguments = mapOf("name" to "My NAS", "baseUrl" to baseUrl, "username" to username)),
        client.asScopedHttpClient(),
    )

    private fun query() = MediaFetchRequest(
        subjectId = "1", episodeId = "2", subjectNameCN = "Anime", subjectNames = listOf("Anime"),
        episodeSort = EpisodeSort(1), episodeName = "Episode 1",
    )

    private fun multistatus(vararg entries: String) = """<d:multistatus xmlns:d="DAV:">${entries.joinToString("")}</d:multistatus>"""

    private fun entry(href: String, directory: Boolean = false, size: Long = 0) = """
        <d:response><d:href>$href</d:href><d:propstat><d:prop>
        <d:resourcetype>${if (directory) "<d:collection/>" else ""}</d:resourcetype>
        <d:getcontentlength>$size</d:getcontentlength>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
    """.trimIndent()

    private fun MockRequestHandleScope.xmlResponse(body: String) =
        respond(body, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()))
}
