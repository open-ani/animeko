/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.dmhy.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.readString
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.dmhy.impl.protocol.Network
import me.him188.ani.utils.ktor.asScopedHttpClient
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals

class DmhyPagedSourceImplTest {
    /**
     * 模拟動漫花園列表页, 每页 [pageSize] 条, 共 [totalPages] 页. 超出的页返回空表格.
     */
    private class FakeDmhy(
        private val totalPages: Int,
        private val pageSize: Int = 80,
    ) {
        val requests = mutableListOf<Url>()

        val network = Network(
            HttpClient(
                MockEngine { request ->
                    requests += request.url
                    val page = request.url.encodedPath.substringAfter("/page/", "1").toInt()
                    val ids = if (page <= totalPages) (page - 1) * pageSize until page * pageSize else IntRange.EMPTY
                    respond(
                        listPage(ids),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    register(ContentType.Text.Html, HtmlConverter)
                }
            }.asScopedHttpClient(),
        )

        private fun listPage(ids: IntRange): String = buildString {
            append("<html><body><table class=\"tablesorter\"><tbody>")
            for (id in ids) {
                append(
                    """
                    <tr>
                      <td><span>2026/10/07 12:00</span></td>
                      <td><a href="/topics/list/sort_id/2">動畫</a></td>
                      <td><a href="/topics/view/${id}_test.html">[Group] Test - 01 [1080p]</a></td>
                      <td><a href="magnet:?xt=urn:btih:$id">magnet</a></td>
                      <td>1.0GB</td>
                      <td><a href="/topics/list/user_id/1">author</a></td>
                    </tr>
                    """,
                )
            }
            append("</tbody></table></body></html>")
        }
    }

    private object HtmlConverter : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): OutgoingContent? = null

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any =
            Jsoup.parse(content.readRemaining().readString())
    }

    private fun search(keywords: String, dmhy: FakeDmhy) = DmhyPagedSourceImpl(
        DownloadSearchQuery(keywords = keywords, category = TopicCategory.ANIME, allowAny = true),
        dmhy.network,
    )

    @Test
    fun `ASCII colon in keyword is sent as space`() = runTest {
        val dmhy = FakeDmhy(totalPages = 1)
        search("Re:ゼロから始める異世界生活", dmhy).results.toList()
        assertEquals("Re ゼロから始める異世界生活", dmhy.requests.first().parameters["keyword"])
    }

    @Test
    fun `stops at empty page`() = runTest {
        val dmhy = FakeDmhy(totalPages = 2)
        val results = search("keyword", dmhy).results.toList()
        assertEquals(160, results.size)
        assertEquals(3, dmhy.requests.size)
    }

    @Test
    fun `stops paging after 1000 results when site keeps returning full pages`() = runTest {
        val dmhy = FakeDmhy(totalPages = 5000)
        val results = search("keyword", dmhy).results.toList()
        assertEquals(13, dmhy.requests.size)
        assertEquals(13 * 80, results.size)
    }

    @Test
    fun `blank keyword does not search`() = runTest {
        val dmhy = FakeDmhy(totalPages = 5000)
        val results = search(":", dmhy).results.toList()
        assertEquals(0, results.size)
        assertEquals(0, dmhy.requests.size)
    }
}
