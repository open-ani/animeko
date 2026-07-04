/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.info

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniInfoServiceTest {
    private fun serviceReturning(status: HttpStatusCode, body: String): AniInfoService {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        return AniInfoService(
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                expectSuccess = true
            },
        )
    }

    @Test
    fun `getTrends maps ranked subjects`() = runTest {
        val service = serviceReturning(
            HttpStatusCode.OK,
            """
            {
              "trendingSubjects": [
                {"bangumiId": 425998, "nameCn": "葬送的芙莉莲", "imageLarge": "https://img.example.com/1.jpg"},
                {"bangumiId": 400602, "nameCn": "间谍过家家", "imageLarge": "https://img.example.com/2.jpg"},
                {"bangumiId": 123456, "nameCn": "第三名", "imageLarge": "https://img.example.com/3.jpg"}
              ]
            }
            """.trimIndent(),
        )

        val result = service.getTrends(GetTrendsInput(limit = 2))

        assertTrue(result.ok, result.summary)
        assertEquals(2, result.subjects.size)
        assertEquals(1, result.subjects[0].rank)
        assertEquals(425998L, result.subjects[0].subjectId)
        assertEquals("葬送的芙莉莲", result.subjects[0].nameCn)
        assertEquals(2, result.subjects[1].rank)
        assertEquals(400602L, result.subjects[1].subjectId)
    }

    @Test
    fun `getTrends limit zero returns all`() = runTest {
        val service = serviceReturning(
            HttpStatusCode.OK,
            """
            {
              "trendingSubjects": [
                {"bangumiId": 1, "nameCn": "a", "imageLarge": ""},
                {"bangumiId": 2, "nameCn": "b", "imageLarge": ""}
              ]
            }
            """.trimIndent(),
        )

        val result = service.getTrends(GetTrendsInput(limit = 0))
        assertEquals(2, result.subjects.size)
    }

    @Test
    fun `getTrends surfaces upstream error`() = runTest {
        val service = serviceReturning(HttpStatusCode.TooManyRequests, """{"error": "Rate exceeded."}""")

        val result = service.getTrends(GetTrendsInput())

        assertFalse(result.ok)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.first().contains("429"), result.errors.first())
    }
}
