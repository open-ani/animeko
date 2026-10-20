/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.him188.ani.app.data.repository.RepositoryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AniFeatureFlagServiceTest {
    @Test
    fun `returns the properties as sent by the server`() = runTest {
        val server = NotificationTestServer {
            respondJson("""{"enabled":true,"sample_rate":0.2,"variant":"b","nested":{"a":1},"list":[1,"x"]}""")
        }

        val properties = server.featureFlagService.getFeatureFlag("play_flow_report")

        assertEquals(
            buildJsonObject {
                put("enabled", true)
                put("sample_rate", 0.2)
                put("variant", "b")
                putJsonObject("nested") { put("a", 1) }
                put("list", buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive("x")) })
            },
            properties,
        )
        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v2/feature-flags/play_flow_report", request.url.encodedPath)
    }

    @Test
    fun `empty object when the request hits no value`() = runTest {
        val server = NotificationTestServer { respondJson("{}") }
        assertEquals(JsonObject(emptyMap()), server.featureFlagService.getFeatureFlag("play_flow_report"))
    }

    @Test
    fun `unknown flag is an empty object`() = runTest {
        val server = NotificationTestServer { respond("", HttpStatusCode.NotFound) }
        assertEquals(JsonObject(emptyMap()), server.featureFlagService.getFeatureFlag("future_flag"))
    }

    @Test
    fun `server errors become repository exceptions`() = runTest {
        val server = NotificationTestServer { respond("", HttpStatusCode.InternalServerError) }
        assertFailsWith<RepositoryException> { server.featureFlagService.getFeatureFlag("play_flow_report") }
    }
}
