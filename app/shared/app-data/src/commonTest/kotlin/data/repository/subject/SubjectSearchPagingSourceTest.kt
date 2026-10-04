/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 本地过滤 (评分人数, 已看过/抛弃) 可能清空整页, 分页只能以服务端返回空页作为结束 #3505.
 */
class SubjectSearchPagingSourceTest {
    @Test
    fun `rank search continues past a page whose subjects are all filtered out`() = runTest {
        // 按评分排序时, 开头整页都是一两个人打 10 分的冷门条目, 本地按评分人数过滤后为空
        val server = Server(
            0 to List(PAGE_SIZE) { SearchItem(id = it + 1, ratingTotal = 1) },
            PAGE_SIZE to listOf(SearchItem(id = 400602, ratingTotal = 36360), SearchItem(id = 99, ratingTotal = 3)),
        )

        val ids = server.source(SearchSort.RANK).loadAllIds()

        assertEquals(listOf(400602), ids)
        assertEquals(listOf(0, PAGE_SIZE, PAGE_SIZE * 2), server.requestedOffsets)
    }

    @Test
    fun `excluding done and dropped subjects does not end search early`() = runTest {
        val server = Server(
            0 to listOf(SearchItem(id = 1, ratingTotal = 100)),
            PAGE_SIZE to listOf(SearchItem(id = 2, ratingTotal = 100)),
        )

        val ids = server.source(SearchSort.MATCH, doneOrDroppedIds = listOf(1)).loadAllIds()

        assertEquals(listOf(2), ids)
        assertEquals(listOf(0, PAGE_SIZE, PAGE_SIZE * 2), server.requestedOffsets)
    }

    private class SearchItem(val id: Int, val ratingTotal: Int)

    /**
     * 按请求的 offset 返回对应页, 没有配置的 offset 返回空页.
     */
    private class Server(vararg pages: Pair<Int, List<SearchItem>>) {
        private val pagesByOffset = pages.toMap()
        val requestedOffsets = mutableListOf<Int>()

        private val engine = MockEngine { request ->
            val offset = request.url.parameters["offset"]!!.toInt()
            requestedOffsets += offset
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
            doneOrDroppedIds: List<Int>? = null,
        ): PagingSource<Int, BatchSubjectDetails> {
            val repository = SubjectSearchRepository(AniSubjectSearchService(api), Collections(doneOrDroppedIds.orEmpty()))
            return repository.SubjectSearchPagingSource(
                ignoreDoneAndDropped = { doneOrDroppedIds != null },
                searchQuery = SubjectSearchQuery("", year = 2023, sort = sort),
            )
        }

        private fun pageJson(items: List<SearchItem>): String = items.joinToString(
            prefix = """{"items":[""",
            postfix = "]}",
        ) { item ->
            """
                {"id":${item.id},"name":"","nameCn":"","summary":"","imageLarge":"","nsfw":false,
                "airDate":"2023-09-29","score":"8.5","rank":0,"ratingTotal":${item.ratingTotal},
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

    private class Collections(private val doneOrDroppedIds: List<Int>) : SubjectCollectionRepository() {
        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            flowOf(doneOrDroppedIds)

        override suspend fun invalidateCache(subjectIds: List<Int>) = unsupported()
        override suspend fun invalidateAllCaches() = unsupported()
        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = unsupported()
        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = unsupported()
        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> = unsupported()

        override fun cachedValidSubjectIds(): Flow<List<Int>> = unsupported()
        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ) = unsupported()

        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = unsupported()

        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ) = unsupported()

        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) = unsupported()
        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> = unsupported()
        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> = unsupported()
        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            unsupported()

        override suspend fun performBangumiFullSync() = unsupported()
        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by SubjectSearchPagingSource")
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
