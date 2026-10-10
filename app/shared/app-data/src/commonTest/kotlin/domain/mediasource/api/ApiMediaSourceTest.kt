/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.domain.mediasource.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.decodeFromStringOrNull
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiMediaSourceTest {
    @Test
    fun `configuration requires absolute HTTP URLs and known variables`() {
        assertFailsWith<IllegalArgumentException> { ApiMediaSourceArguments().validate() }
        assertFailsWith<IllegalArgumentException> { requireHttpUrl("/relative") }
        assertFailsWith<IllegalArgumentException> { requireHttpUrl("file:///video.mp4") }
        assertFailsWith<IllegalStateException> {
            fixture().copy(search = fixture().search.copy(request = ApiRequest("https://api.test/{unknown}"))).validate()
        }
    }
    private fun config(arguments: ApiMediaSourceArguments) = MediaSourceConfig(
        serializedArguments = Json.encodeToJsonElement(ApiMediaSourceArguments.serializer(), arguments),
    )

    private fun fixture() = ApiMediaSourceArguments(
        name = "POST fixture", headers = mapOf("apikey" to "public-test"),
        search = ApiSearch(ApiRequest("https://api.test/search", "POST", body = Json.parseToJsonElement(
            """{"q":"{keyword}","page":"{page}","size":"{pageSize}"}""")),
            aliasesPath = "$.aliases[*]", subjectUrl = "https://site.test/anime/{subjectId}", paginate = true, pageSize = 1),
        detail = ApiDetail(ApiRequest("https://api.test/detail", "POST", body = Json.parseToJsonElement("""{"id":"{subjectId}"}""")),
            episodeUrl = "https://site.test/anime/{subjectId}/play/{episodeId}?source={channelId}"),
        playback = ApiPlayback(ApiRequest("https://api.test/play", "POST", body = Json.parseToJsonElement(
            """{"id":"{episodeId}","source":"{channelId}"}""")), successPath = "$.ok", candidatesPath = "$.candidates[*]",
            candidateIdPath = "$.source", candidateUrlPath = "$.url", videoHeaders = mapOf("Referer" to "https://site.test/")),
    )

    @Test
    fun `typed body substitutions escape quotes and URL components`() {
        val vars = mapOf("id" to JsonPrimitive(7), "keyword" to JsonPrimitive("a\" & 中文"))
        val body = substituteJson(Json.parseToJsonElement("""{"id":"{id}","q":"{keyword}","label":"id={id}"}"""), vars).jsonObject
        assertEquals(JsonPrimitive(7), body["id"])
        assertEquals(vars["keyword"], body["q"])
        assertEquals(JsonPrimitive("id=7"), body["label"])
        assertEquals("https://api.test/?q=a%22%20%26%20%E4%B8%AD%E6%96%87", substitute("https://api.test/?q={keyword}", vars, true))
        assertFailsWith<IllegalStateException> { substitute("{missing}", vars) }
    }

    @Test
    fun `POST browsing paginates and playback remains fresh after recreation`() = runTest {
        var playbackCalls = 0
        val engine = MockEngine { request ->
            assertEquals("public-test", request.headers["apikey"])
            assertEquals(HttpMethod.Post, request.method)
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            val response = when (request.url.encodedPath) {
                "/search" -> {
                    assertEquals(JsonPrimitive(1), body["size"])
                    if (body["page"] == JsonPrimitive(1)) """[{"id":42,"title":"Title","aliases":["Alias"]}]"""
                    else "[]"
                }
                "/detail" -> {
                    assertEquals(JsonPrimitive(42), body["id"])
                    """{"sources":[{"id":"A","name":"Main","episodes":[{"id":9,"title":"One","number":1},{"id":10,"title":"Two","number":2}]}]}"""
                }
                "/play" -> {
                    assertEquals(JsonPrimitive(9), body["id"])
                    assertEquals(JsonPrimitive("A"), body["source"])
                    playbackCalls++
                    """{"ok":true,"url":"https://video.test/wrong.m3u8","candidates":[{"source":"B","url":"https://video.test/other.m3u8"},{"source":"A","url":"https://video.test/$playbackCalls.m3u8"}]}"""
                }
                else -> error("Unexpected request")
            }
            respond(response, headers = headersOf("Content-Type", "application/json"))
        }
        HttpClient(engine).use { client ->
            val arguments = fixture()
            val source = ApiMediaSource("source", config(arguments), client.asScopedHttpClient())
            val subject = source.searchSubjects("Title").single()
            val channel = source.browseSubject(subject).single()
            assertEquals(2, channel.episodes.size)
            val media = source.createMedia(subject, channel.name, channel.episodes[0], EpisodeSort(1))
            assertEquals("https://site.test/anime/42/play/9?source=A", media.originalUrl)
            val recreated = ApiMediaSource("source", config(arguments), client.asScopedHttpClient())
            assertEquals("https://video.test/1.m3u8", recreated.resolveVideo(media).m3u8Url)
            assertEquals("https://video.test/2.m3u8", recreated.resolveVideo(media).m3u8Url)
            assertFalse("apikey" in recreated.resolveVideo(media).headers)
            val automatic = source.fetch(MediaFetchRequest(subjectId = "1", episodeId = "1", subjectNames = listOf("Alias"),
                episodeSort = EpisodeSort(1), episodeName = "One")).results.toList()
            assertEquals(2, automatic.size)
            assertEquals(media.mediaId, automatic[0].media.mediaId)
        }
    }

    @Test
    fun `GET API with nested data and no channels supports string identifiers`() = runTest {
        val args = ApiMediaSourceArguments(
            search = ApiSearch(ApiRequest("https://api.test/find?q={keyword}"), itemsPath = "$.data.hits[*]",
                idPath = "$.key", namePath = "$.label", subjectUrl = "https://site.test/{subjectId}"),
            detail = ApiDetail(ApiRequest("https://api.test/show/{subjectId}"), channelsPath = "$", channelIdPath = "",
                channelNamePath = "", episodesPath = "$.data.parts[*]", episodeIdPath = "$.key", episodeNamePath = "$.label",
                episodeNumberPath = "", episodeUrl = "https://site.test/{subjectId}/{episodeId}"),
            playback = ApiPlayback(ApiRequest("https://api.test/video/{episodeId}"), urlPath = "$.data.stream"),
        )
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            val response = when (request.url.encodedPath) {
                "/find" -> { assertEquals("A & B", request.url.parameters["q"]); """{"data":{"hits":[{"key":"s/1","label":"A & B"}]}}""" }
                "/show/s%2F1" -> """{"data":{"parts":[{"key":"e-9","label":"Extra"}]}}"""
                "/video/e-9" -> """{"data":{"stream":"https://video.test/extra.mp4"}}"""
                else -> error(request.url.encodedPath)
            }
            respond(response, headers = headersOf("Content-Type", "application/json"))
        }
        HttpClient(engine).use { client ->
            val source = ApiMediaSource("get-source", config(args), client.asScopedHttpClient())
            val subject = source.searchSubjects("A & B").single()
            val channel = source.browseSubject(subject).single()
            val media = source.createMedia(subject, null, channel.episodes.single(), EpisodeSort(3))
            assertEquals("https://video.test/extra.mp4", source.resolveVideo(media).m3u8Url)
            assertEquals(EpisodeRange.single(EpisodeSort(3)), media.episodeRange)
        }
    }

    @Test
    fun `codec round trip preserves arbitrary requests and multiple configurations`() {
        val codecs = MediaSourceCodecManager()
        val args = fixture()
        val data = codecs.decodeFromStringOrNull(codecs.serializeToString(listOf(args, args.copy(name = "Another"))))!!
        assertEquals(2, data.mediaSources.size)
        assertEquals(args, codecs.decode(data.mediaSources[0]))
        assertTrue(ApiMediaSource.Factory().allowMultipleInstances)
    }

    @Test
    fun `repeated pagination stops and HTTP errors propagate`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("""[{"id":1,"title":"Title"}]""", headers = headersOf("Content-Type", "application/json"))
        }
        HttpClient(engine).use { client ->
            val source = ApiMediaSource("repeat", config(fixture()), client.asScopedHttpClient())
            assertEquals(1, source.searchSubjects("Title").size)
            assertEquals(2, calls)
        }
        HttpClient(MockEngine { respond("denied", HttpStatusCode.Forbidden) }).use { client ->
            val source = ApiMediaSource("error", config(fixture()), client.asScopedHttpClient())
            assertFailsWith<IllegalStateException> { source.searchSubjects("Title") }
        }
    }
}
