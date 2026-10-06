/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 搜索的过滤全部由服务端完成, 分页源只负责翻译查询参数, 并在服务端返回空页时结束.
 */
class SubjectSearchPagingSourceTest {
    @Test
    fun `rank search asks server for ranked subjects sorted by rating`() = runTest {
        val server = Server(0 to listOf(1))

        server.source(SearchSort.RANK).loadAllIds()

        assertEquals("ratingDesc", server.parameter("sortBy"))
        assertEquals(">=1", server.parameter("ranks"))
    }

    @Test
    fun `other sorts do not filter by rank`() = runTest {
        val server = Server(0 to listOf(1))

        server.source(SearchSort.MATCH).loadAllIds()

        assertNull(server.parameter("ranks"))
    }

    @Test
    fun `ignoring done and dropped asks server to exclude them`() = runTest {
        val server = Server(0 to listOf(1))

        server.source(SearchSort.MATCH, ignoreDoneAndDropped = true).loadAllIds()

        assertEquals("DONE,DROPPED", server.parameter("excludeCollectionTypes"))
    }

    @Test
    fun `collection exclusion is off by default`() = runTest {
        val server = Server(0 to listOf(1))

        server.source(SearchSort.MATCH).loadAllIds()

        assertNull(server.parameter("excludeCollectionTypes"))
    }

    @Test
    fun `pagination keeps every subject and stops at server empty page`() = runTest {
        val server = Server(
            0 to List(PAGE_SIZE) { it + 1 },
            PAGE_SIZE to listOf(400602),
        )

        val ids = server.source(SearchSort.RANK).loadAllIds()

        assertEquals(List(PAGE_SIZE) { it + 1 } + 400602, ids)
        assertEquals(listOf("0", "20", "40"), server.requests.map { it["offset"] })
    }

    /**
     * 按请求的 offset 返回对应页的条目 id, 没有配置的 offset 返回空页.
     */
    private class Server(vararg pages: Pair<Int, List<Int>>) {
        private val pagesByOffset = pages.toMap()
        val requests = mutableListOf<Parameters>()

        /** 请求里 [name] 参数的值. 每一页的请求都必须带同样的值. */
        fun parameter(name: String): String? = requests.map { it[name] }.distinct().single()

        private val engine = MockEngine { request ->
            requests += request.url.parameters
            val offset = request.url.parameters["offset"]!!.toInt()
            respond(
                pageJson(pagesByOffset[offset].orEmpty()),
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        private val api = object : ApiInvoker<SubjectsAniApi> {
            private val client = SubjectsAniApi(baseUrl = "http://test", httpClientEngine = engine)
            override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R = action(client)
        }

        fun source(
            sort: SearchSort,
            ignoreDoneAndDropped: Boolean = false,
        ): PagingSource<Int, BatchSubjectDetails> {
            val repository = SubjectSearchRepository(AniSubjectSearchService(api))
            return repository.SubjectSearchPagingSource(
                ignoreDoneAndDropped = { ignoreDoneAndDropped },
                searchQuery = SubjectSearchQuery("", year = 2023, sort = sort),
            )
        }

        private fun pageJson(ids: List<Int>): String = ids.joinToString(
            prefix = """{"items":[""",
            postfix = "]}",
        ) { id ->
            """
                {"id":$id,"name":"","nameCn":"","summary":"","imageLarge":"","nsfw":false,
                "airDate":"2023-09-29","score":"8.5","rank":0,"ratingTotal":1,
                "favorite":{"wish":0,"done":0,"doing":0,"onHold":0,"dropped":0},"tags":[],
                "mainEpisodeCount":12,"lightRelatedPersonInfoList":[]}
            """.trimIndent()
        }
    }

    /**
     * 从第一页加载到 `nextKey == null`, 返回所有页里的条目 id.
     */
    private suspend fun PagingSource<Int, BatchSubjectDetails>.loadAllIds(): List<Int> {
        val ids = mutableListOf<Int>()
        var params: PagingSource.LoadParams<Int> =
            PagingSource.LoadParams.Refresh(key = 0, loadSize = PAGE_SIZE, placeholdersEnabled = false)
        while (true) {
            val page = assertIs<PagingSource.LoadResult.Page<Int, BatchSubjectDetails>>(load(params))
            page.data.mapTo(ids) { it.subjectInfo.subjectId }
            val nextKey = page.nextKey ?: return ids
            params = PagingSource.LoadParams.Append(key = nextKey, loadSize = PAGE_SIZE, placeholdersEnabled = false)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
