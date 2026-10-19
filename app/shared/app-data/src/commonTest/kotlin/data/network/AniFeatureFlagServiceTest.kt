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
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.featureflag.FeatureFlagValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AniFeatureFlagServiceTest {
    @Test
    fun `returns the flag values as sent by the server`() = runTest {
        val server = NotificationTestServer {
            respondJson("""{"flags":{"play_flow_analytics":{"enabled":true,"threshold":0.2},"future_flag":{"enabled":false},"on":{"enabled":true}}}""")
        }

        val flags = server.featureFlagService.getFeatureFlags()

        assertEquals(
            mapOf(
                "play_flow_analytics" to FeatureFlagValue(enabled = true, threshold = 0.2f),
                "future_flag" to FeatureFlagValue(enabled = false),
                "on" to FeatureFlagValue(enabled = true),
            ),
            flags,
        )
        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v2/feature-flags", request.url.encodedPath)
    }

    @Test
    fun `empty map when the server has no flags`() = runTest {
        val server = NotificationTestServer { respondJson("""{"flags":{}}""") }
        assertEquals(emptyMap(), server.featureFlagService.getFeatureFlags())
    }

    @Test
    fun `server errors become repository exceptions`() = runTest {
        val server = NotificationTestServer { respond("", HttpStatusCode.InternalServerError) }
        assertFailsWith<RepositoryException> { server.featureFlagService.getFeatureFlags() }
    }
}
