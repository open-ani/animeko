/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.PagingSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.trending.TrendingRankingItemInfo
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrendingRankingPagingSourceTest {
    @Test
    fun `maps rank, heat and subject`() = runTest {
        val server = Server(total = 1)

        val page = assertIs<Page>(server.source.load(refresh(30)))

        val item = page.data.single()
        assertEquals(1, item.rank)
        assertEquals(1000, item.heat)
        assertEquals(8501, item.subject.subjectInfo.subjectId)
        assertEquals("条目 8501", item.subject.subjectInfo.nameCn)
        assertEquals(12, item.subject.mainEpisodeCount)
    }

    @Test
    fun `pages by offset until the total is reached`() = runTest {
        val server = Server(total = 70)

        val first = assertIs<Page>(server.source.load(refresh(30)))
        assertEquals(30, first.nextKey)
        val second = assertIs<Page>(server.source.load(append(30, 30)))
        assertEquals(60, second.nextKey)
        val last = assertIs<Page>(server.source.load(append(60, 30)))
        assertNull(last.nextKey)

        assertEquals((1..70).toList(), (first.data + second.data + last.data).map { it.rank })
        assertEquals(listOf("0", "30", "60"), server.requests.map { it["offset"] })
    }

    @Test
    fun `limit never exceeds what the server accepts`() = runTest {
        val server = Server(total = 1000)

        val page = assertIs<Page>(server.source.load(refresh(300)))

        assertEquals("100", server.requests.single()["limit"])
        assertEquals(100, page.nextKey)
    }

    @Test
    fun `server error becomes a load error`() = runTest {
        val server = Server(total = 1000, failing = true)

        assertIs<PagingSource.LoadResult.Error<Int, TrendingRankingItemInfo>>(server.source.load(refresh(30)))
    }

    /**
     * 排行共 [total] 个条目, 第 n 名的条目 id 为 8500 + n, 热度为 1001 - n.
     */
    private class Server(private val total: Int, private val failing: Boolean = false) {
        val requests = mutableListOf<Parameters>()

        private val engine = MockEngine { request ->
            requests += request.url.parameters
            if (failing) return@MockEngine respond("error", HttpStatusCode.InternalServerError)
            val offset = request.url.parameters["offset"]!!.toInt()
            val limit = request.url.parameters["limit"]!!.toInt()
            val ranks = (offset + 1..minOf(offset + limit, total))
            respond(
                ranks.joinToString(prefix = """{"total":$total,"items":[""", postfix = "]}") { itemJson(it) },
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        private val api = object : ApiInvoker<TrendsAniApi> {
            private val client = TrendsAniApi(baseUrl = "http://test", httpClientEngine = engine)
            override suspend fun <R> invoke(action: suspend TrendsAniApi.() -> R): R = action(client)
        }

        val source = TrendingRankingPagingSource(api, EmptyCoroutineContext)

        private fun itemJson(rank: Int): String {
            val id = 8500 + rank
            return """
                {"trendingRank":$rank,"heat":${1001 - rank},"subject":{"id":$id,"name":"Subject $id",
                "nameCn":"条目 $id","summary":"","imageLarge":"","nsfw":false,"airDate":"2026-10-01",
                "score":"7.5","rank":100,"ratingTotal":500,
                "favorite":{"wish":0,"done":0,"doing":0,"onHold":0,"dropped":0},"tags":[],
                "mainEpisodeCount":12,"lightRelatedPersonInfoList":[]}}
            """.trimIndent()
        }
    }

    private fun refresh(loadSize: Int) = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )

    private fun append(key: Int, loadSize: Int) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )
}

private typealias Page = PagingSource.LoadResult.Page<Int, TrendingRankingItemInfo>
