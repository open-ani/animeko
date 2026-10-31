/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.runBlocking
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "ANI_EMBY_TEST_BROWSE_SUBJECT_ID", matches = ".+")
class EmbyServerBrowsingTest {
    @Test
    fun `manual search selects the expected season episode and reads its stream`() = runBlocking {
        createDefaultHttpClient().use { client ->
            val source = EmbyMediaSource(
                MediaSourceConfig(
                    arguments = mapOf(
                        "baseUrl" to checkNotNull(System.getenv("ANI_EMBY_TEST_URL")),
                        "authMode" to EmbyMediaSource.AUTH_MODE_USERNAME_PASSWORD,
                        "username" to checkNotNull(System.getenv("ANI_EMBY_TEST_USERNAME")),
                        "password" to System.getenv("ANI_EMBY_TEST_PASSWORD").orEmpty(),
                    ),
                ),
                client.asScopedHttpClient(),
                instanceId = "manual-browse-verification",
            )
            val subjectId = checkNotNull(System.getenv("ANI_EMBY_TEST_BROWSE_SUBJECT_ID"))
            val itemId = checkNotNull(System.getenv("ANI_EMBY_TEST_ITEM_ID"))
            val channelLabel = checkNotNull(System.getenv("ANI_EMBY_TEST_CHANNEL_LABEL"))
            val subjects = source.searchSubjects(checkNotNull(System.getenv("ANI_EMBY_TEST_BROWSE_KEYWORD")))
            val subject = subjects.single { it.url.endsWith("/Items/$subjectId") }
            val channel = source.browseSubject(subject).single { it.label == channelLabel }
            val episode = channel.episodes.single { it.url.endsWith("/Items/$itemId") }
            val sort = EpisodeSort(checkNotNull(System.getenv("ANI_EMBY_TEST_EPISODE_SORT")))
            assertEquals(sort, episode.episodeSort)
            val media = source.createMedia(subject, channel.name, episode, sort)
            assertEquals(EpisodeRange.single(sort), media.episodeRange)
            val video = assertNotNull(source.resolveApiVideo(media))
            client.prepareGet(video.m3u8Url) {
                video.headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Range, "bytes=0-1023")
            }.execute { response ->
                assertEquals(HttpStatusCode.PartialContent, response.status)
                assertTrue(response.bodyAsChannel().readAvailable(ByteArray(1024)) > 0)
            }
        }
    }
}
