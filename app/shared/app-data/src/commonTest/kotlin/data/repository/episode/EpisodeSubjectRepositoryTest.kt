/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EpisodeSubjectRepositoryTest {
    private fun repository(status: HttpStatusCode, subjectId: Long = 12): EpisodeSubjectRepository {
        val client = EpisodesAniApi(
            baseUrl = "http://test",
            httpClientEngine = MockEngine { request ->
                assertEquals("/v2/episodes/102", request.url.encodedPath)
                respond(
                    """{"episodeId":102,"subjectId":$subjectId,"sort":"13","ep":"1","type":"MAIN","name":"","nameCn":"","description":""}""",
                    status, headersOf("Content-Type", "application/json"),
                )
            },
            httpClientConfig = { it.expectSuccess = true },
        )
        return EpisodeSubjectRepository(object : ApiInvoker<EpisodesAniApi> {
            override suspend fun <R> invoke(action: suspend EpisodesAniApi.() -> R): R = action(client)
        })
    }

    @Test
    fun `lookup uses episode endpoint and reads subject id`() = runTest {
        assertEquals(12, repository(HttpStatusCode.OK).getSubjectId(102))
    }

    @Test
    fun `missing episode returns null`() = runTest {
        assertNull(repository(HttpStatusCode.NotFound).getSubjectId(102))
    }

    @Test
    fun `invalid subject id returns null`() = runTest {
        assertNull(repository(HttpStatusCode.OK, 0).getSubjectId(102))
        assertNull(repository(HttpStatusCode.OK, Int.MAX_VALUE.toLong() + 1).getSubjectId(102))
    }

    @Test
    fun `server errors are reported`() = runTest {
        assertFailsWith<RepositoryException> { repository(HttpStatusCode.InternalServerError).getSubjectId(102) }
    }

    @Test
    fun `cancellation propagates`() = runTest {
        val repository = EpisodeSubjectRepository(object : ApiInvoker<EpisodesAniApi> {
            override suspend fun <R> invoke(action: suspend EpisodesAniApi.() -> R): R = throw CancellationException()
        })
        assertFailsWith<CancellationException> { repository.getSubjectId(102) }
    }
}
