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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** 由环境变量提供服务器、账号和片名的真实网络验证，普通测试环境跳过。 */
@EnabledIfEnvironmentVariable(named = "ANI_EMBY_TEST_URL", matches = ".+")
class EmbyServerConnectionTest {
    @Test
    fun `password login searches library and reads a video range`() = runBlocking {
        createDefaultHttpClient().use { client ->
            val title = checkNotNull(System.getenv("ANI_EMBY_TEST_TITLE"))
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
                instanceId = "development-verification",
            )
            assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
            val expectedItemId = System.getenv("ANI_EMBY_TEST_ITEM_ID")
            val media = source.fetch(
                MediaFetchRequest(
                    subjectId = "", episodeId = "", subjectNameCN = title, subjectNames = listOf(title),
                    episodeSort = EpisodeSort(1), episodeName = title,
                ),
            ).results.toList().firstOrNull { match ->
                expectedItemId == null ||
                    (match.media.download as? ResourceLocation.WebVideo)?.apiParameters?.get("itemId") == expectedItemId
            }?.media
            val video = assertNotNull(source.resolveApiVideo(assertNotNull(media)))
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
