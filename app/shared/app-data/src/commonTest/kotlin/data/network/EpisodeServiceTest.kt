/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EpisodeServiceTest {
    private suspend fun withService(
        status: HttpStatusCode = HttpStatusCode.OK,
        subjectId: Long = 622288,
        body: String? = null,
        expectedPath: String = "/v2/episodes/1741638",
        block: suspend (EpisodeService) -> Unit,
    ) {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(ServerListFeatureConfig.MAGIC_ANI_SERVER_HOST.lowercase(), request.url.host.lowercase())
            assertEquals(expectedPath, request.url.encodedPath)
            assertEquals("Bearer test-ani-token", request.headers[HttpHeaders.Authorization])
            respond(
                body ?: """{"episodeId":1741638,"subjectId":$subjectId,"sort":"1","type":"MAIN","name":"Episode","nameCn":"","description":""}""",
                status,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
            defaultRequest { headers.append(HttpHeaders.Authorization, "Bearer test-ani-token") }
        }
        try {
            val provider = AniApiProvider(
                client = client.asScopedHttpClient(),
            )
            block(EpisodeServiceImpl(provider.subjectApi, provider.episodesApi))
        } finally {
            client.close()
        }
    }

    @Test
    fun `ani episode endpoint returns parent subject id without a subject parameter`() = runTest {
        withService { assertEquals(622288, it.getSubjectId(1741638)) }
    }

    @Test
    fun `maximum positive Int subject id is accepted`() = runTest {
        withService(subjectId = Int.MAX_VALUE.toLong()) { assertEquals(Int.MAX_VALUE, it.getSubjectId(1741638)) }
    }

    @Test
    fun `invalid episode response propagates a decoding failure`() = runTest {
        withService(body = "{}") {
            assertFailsWith<JsonConvertException> { it.getSubjectId(1741638) }
        }
    }

    @Test
    fun `404 returns no subject`() = runTest {
        withService(status = HttpStatusCode.NotFound) { assertNull(it.getSubjectId(1741638)) }
    }

    @Test
    fun `server failure propagates to candidate fallback`() = runTest {
        withService(status = HttpStatusCode.ServiceUnavailable) {
            assertFailsWith<ServerResponseException> { it.getSubjectId(1741638) }
        }
    }

    @Test
    fun `invalid parent subject id is rejected`() = runTest {
        for (id in listOf(0L, -1L, Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)) {
            withService(subjectId = id) { assertNull(it.getSubjectId(1741638)) }
        }
    }

    @Test
    fun `known subject episode lookup keeps the existing Ani route`() = runTest {
        withService(expectedPath = "/v2/subjects/622288/episodes/1741638") {
            assertEquals(1741638, it.getEpisodeCollectionById(622288, 1741638)?.episodeId)
        }
    }
}
