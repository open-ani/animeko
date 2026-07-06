/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandshakeFailureDomainAdvisorTest {
    @Test
    fun analyzeIfNeeded_returnsDomainHints_forHandshakeFailure() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("www.bing.com", request.url.host)
                assertEquals("rss", request.url.parameters["format"])
                respond(
                    content = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>趣动漫最新地址</title>
                          <link>https://www.qdm8.cc/</link>
                          <description>趣动漫备用网址</description>
                        </item>
                        <item>
                          <title>趣动漫旧域名</title>
                          <link>https://www.qdm8.com/</link>
                          <description>旧站</description>
                        </item>
                        <item>
                          <title>趣动漫讨论</title>
                          <link>https://zhuanlan.zhihu.com/p/123</link>
                          <description>知乎</description>
                        </item>
                      </channel>
                    </rss>
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Xml.toString()),
                )
            },
        )

        val result = HandshakeFailureDomainAdvisor(client).analyzeIfNeeded(
            sourceName = "趣动漫",
            mediaSourceSpec = testSpec("https://www.qdm8.com/search/-------------.html?wd={keyword}"),
            exception = SSLHandshakeException("Remote host terminated the handshake"),
        )

        assertNotNull(result)
        assertEquals("qdm8.com", result.currentHost)
        assertEquals(1, result.suggestions.size)
        assertEquals("qdm8.cc", result.suggestions.single().host)
        assertTrue(result.summary.contains("qdm8.cc"))
    }

    @Test
    fun analyzeIfNeeded_returnsNull_forNonHandshakeFailure() = runTest {
        val client = HttpClient(
            MockEngine { error("Search client should not be called") },
        )

        val result = HandshakeFailureDomainAdvisor(client).analyzeIfNeeded(
            sourceName = "趣动漫",
            mediaSourceSpec = testSpec("https://www.qdm8.com/search/-------------.html?wd={keyword}"),
            exception = IllegalStateException("Forbidden"),
        )

        assertNull(result)
    }

    private fun testSpec(searchUrl: String): MediaSourceSpec {
        return MediaSourceSpec(
            factoryId = "web-selector",
            mediaSourceId = "test",
            serializedArguments = buildJsonObject {
                put(
                    "searchConfig",
                    buildJsonObject {
                        put("searchUrl", JsonPrimitive(searchUrl))
                    },
                )
            },
        )
    }
}
