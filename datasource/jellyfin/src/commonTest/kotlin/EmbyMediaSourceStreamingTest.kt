/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmbyMediaSourceStreamingTest {
    @Test
    fun `playback uses static video streaming with an encoded token and server prefix`() = runTest {
        val token = "api+key&value=1"
        val client = HttpClient(MockEngine {
            respond(
                """{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val source = EmbyMediaSource(
                MediaSourceConfig(
                    arguments = mapOf(
                        "baseUrl" to "https://emby.example/emby/",
                        "userId" to "user-id",
                        "apikey" to token,
                    ),
                ),
                client.asScopedHttpClient(),
            )
            val media = source.fetch(
                MediaFetchRequest(
                    subjectId = "1",
                    episodeId = "2",
                    subjectNameCN = "Movie",
                    subjectNames = listOf("Movie"),
                    episodeSort = EpisodeSort(1),
                    episodeName = "Movie",
                ),
            ).results.toList().single().media
            val location = assertIs<ResourceLocation.WebVideo>(media.download)
            assertEquals("123", location.apiParameters?.get("itemId"))
            val url = Url(source.resolveApiVideo(media)!!.m3u8Url)
            assertEquals("/emby/Videos/123/stream", url.encodedPath)
            assertEquals("true", url.parameters["static"])
            assertEquals(token, url.parameters["api_key"])
        } finally {
            client.close()
        }
    }
}
