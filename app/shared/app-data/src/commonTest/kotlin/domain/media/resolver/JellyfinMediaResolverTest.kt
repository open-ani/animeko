/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.player.JellyfinPlaybackQualityRepository
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.jellyfin.JellyfinMediaSource
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.utils.ktor.asScopedHttpClient
import org.openani.mediamp.source.MediaExtraFiles as MediampMediaExtraFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinMediaResolverTest {
    @Test
    fun `recognizes only the Jellyfin item download shape`() {
        assertTrue(isJellyfinMedia(media("source-1")))
        assertFalse(
            isJellyfinMedia(
                media("source-1").copy(
                    originalUrl = "https://video.example.test/watch/episode-1",
                    download = ResourceLocation.HttpStreamingFile(
                        "https://video.example.test/episode-1.mp4",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `matches a unique current instance id`() {
        val source = source("source-2")
        val instance = createTestMediaSourceInstance(
            source = source,
            mediaSourceId = source.mediaSourceId,
            factoryId = FactoryId(JellyfinMediaSource.ID),
        )

        assertEquals(source, findJellyfinSource(media("source-2"), listOf(instance)))
        assertNull(findJellyfinSource(media("source-1"), listOf(instance)))
    }

    @Test
    fun `legacy fixed id falls back only when one Jellyfin instance exists`() {
        val first = source("source-1")
        val second = source("source-2")
        val firstInstance = createTestMediaSourceInstance(
            source = first,
            mediaSourceId = first.mediaSourceId,
            factoryId = FactoryId(JellyfinMediaSource.ID),
        )
        val secondInstance = createTestMediaSourceInstance(
            source = second,
            mediaSourceId = second.mediaSourceId,
            factoryId = FactoryId(JellyfinMediaSource.ID),
        )

        assertEquals(
            first,
            findJellyfinSource(media(JellyfinMediaSource.ID), listOf(firstInstance)),
        )
        assertNull(
            findJellyfinSource(
                media(JellyfinMediaSource.ID),
                listOf(firstInstance, secondInstance),
            ),
        )
    }

    @Test
    fun `stored fixed quality falls back to server-approved original`() = runTest {
        var playbackInfoRequests = 0
        val source = JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to "https://jellyfin.example.test",
                    "userId" to "test-user",
                    "apikey" to "test-key",
                ),
            ),
            client = HttpClient(
                MockEngine {
                    playbackInfoRequests++
                    val supportsDirectPlay = playbackInfoRequests == 2
                    respond(
                        """
                        {
                          "MediaSources": [{
                            "Id": "episode-1",
                            "Bitrate": 6000000,
                            "SupportsDirectPlay": $supportsDirectPlay,
                            "SupportsDirectStream": $supportsDirectPlay,
                            "SupportsTranscoding": true
                          }]
                        }
                        """.trimIndent(),
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }.asScopedHttpClient(),
            instanceId = "source-1",
        )
        val repository = InMemoryQualityRepository(JellyfinPlaybackQuality.fixed(1_500_000))
        val provider = JellyfinMediaDataProvider(
            source = source,
            itemId = "episode-1",
            originalTitle = "Episode 1",
            extraFiles = MediampMediaExtraFiles.EMPTY,
            qualityRepository = repository,
        )

        val data = provider.open(this)

        assertEquals(
            "https://jellyfin.example.test/Items/episode-1/Download?ApiKey=test-key",
            data.uri,
        )
        assertEquals(2, playbackInfoRequests)
        assertEquals(JellyfinPlaybackQuality.Original, repository.quality)
        assertEquals(JellyfinPlaybackQuality.Original, provider.qualityState.value?.selected)
    }

    private fun source(instanceId: String): JellyfinMediaSource {
        return JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to "https://jellyfin.example.test",
                    "userId" to "test-user",
                    "apikey" to "test-key",
                ),
            ),
            client = HttpClient(MockEngine { error("No request expected") }).asScopedHttpClient(),
            instanceId = instanceId,
        )
    }

    private fun media(mediaSourceId: String) = createTestDefaultMedia(
        mediaId = "episode-1",
        mediaSourceId = mediaSourceId,
        originalUrl = "https://jellyfin.example.test/Items/episode-1",
        download = ResourceLocation.HttpStreamingFile(
            "https://jellyfin.example.test/Items/episode-1/Download?ApiKey=secret",
        ),
        originalTitle = "Episode 1",
        publishedTime = 0,
        properties = createTestMediaProperties(),
        episodeRange = null,
        extraFiles = MediaExtraFiles.EMPTY,
        location = MediaSourceLocation.Lan,
        kind = MediaSourceKind.WEB,
    )

    private class InMemoryQualityRepository(
        var quality: JellyfinPlaybackQuality,
    ) : JellyfinPlaybackQualityRepository {
        override fun preferenceFlow(mediaSourceId: String): Flow<JellyfinPlaybackQuality> {
            return flowOf(quality)
        }

        override suspend fun get(mediaSourceId: String): JellyfinPlaybackQuality = quality

        override suspend fun set(mediaSourceId: String, quality: JellyfinPlaybackQuality) {
            this.quality = quality
        }
    }
}
