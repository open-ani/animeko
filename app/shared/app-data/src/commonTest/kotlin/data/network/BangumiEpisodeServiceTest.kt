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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BangumiEpisodeServiceTest {
    private suspend fun withService(
        status: HttpStatusCode = HttpStatusCode.OK,
        subjectId: Int = 622288,
        block: suspend (BangumiEpisodeService) -> Unit,
    ) {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https://api.bgm.tv/v0/episodes/1741638", request.url.toString())
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(
                """{"id":1741638,"type":0,"name":"Episode","name_cn":"","sort":1,"airdate":"","comment":0,"duration":"","desc":"","disc":0,"subject_id":$subjectId}""",
                status,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        try {
            val api = DefaultApi("https://api.bgm.tv", client)
            val invoker = object : ApiInvoker<DefaultApi> {
                override suspend fun <R> invoke(action: suspend DefaultApi.() -> R): R = action(api)
            }
            block(BangumiEpisodeService(invoker))
        } finally {
            client.close()
        }
    }

    @Test
    fun `public episode endpoint returns parent subject id`() = runTest {
        withService { assertEquals(622288, it.getSubjectId(1741638)) }
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
        for (id in listOf(0, -1)) {
            withService(subjectId = id) { assertNull(it.getSubjectId(1741638)) }
        }
    }
}
