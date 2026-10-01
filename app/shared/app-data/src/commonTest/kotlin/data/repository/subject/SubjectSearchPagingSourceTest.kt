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
import kotlin.test.assertNull

class SubjectSearchPagingSourceTest {
    @Test
    fun `rank search continues past an entire page of low vote subjects`() = runTest {
        val source = source(SearchSort.RANK, listOf(page(1, 1, count = 20), page(400602, 36360), "{\"items\":[]}"))
        val first = assertIs<Page>(source.load(refresh()))
        assertEquals(emptyList(), first.data)
        assertEquals(20, first.nextKey)

        val second = assertIs<Page>(source.load(append(first.nextKey!!)))
        assertEquals(listOf(400602), second.data.map { it.subjectInfo.subjectId })
        assertEquals(40, second.nextKey)
        assertNull(assertIs<Page>(source.load(append(second.nextKey!!))).nextKey)
    }

    @Test
    fun `collection exclusions do not end search at an empty filtered page`() = runTest {
        val source = source(SearchSort.MATCH, listOf(page(400602, 36360), page(296739, 8211)), ignoreDone = true)
        val first = assertIs<Page>(source.load(refresh()))
        assertEquals(emptyList(), first.data)
        assertEquals(20, first.nextKey)
        val second = assertIs<Page>(source.load(append(first.nextKey!!)))
        assertEquals(listOf(296739), second.data.map { it.subjectInfo.subjectId })
    }

    @Test
    fun `server empty page ends search`() = runTest {
        val source = source(SearchSort.RANK, listOf("{\"items\":[]}"))
        val first = assertIs<Page>(source.load(refresh()))
        assertEquals(emptyList(), first.data)
        assertNull(first.nextKey)
    }

    @Test
    fun `consecutive filtered full pages reach later results and stop after server empty page`() = runTest {
        val offsets = mutableListOf<Int>()
        val source = source(
            SearchSort.RANK,
            listOf(page(1, 1, count = 20), page(21, 2, count = 20), page(400602, 36360), "{\"items\":[]}"),
            requestOffsets = offsets,
        )
        val results = loadUntilEnd(source)
        assertEquals(listOf(400602), results.map { it.subjectInfo.subjectId })
        assertEquals(listOf(0, 20, 40, 60), offsets)
    }

    @Test
    fun `short final page keeps results and makes one terminal empty request`() = runTest {
        val offsets = mutableListOf<Int>()
        val source = source(
            SearchSort.RANK,
            listOf(page(400602, 36360, count = 2), "{\"items\":[]}"),
            requestOffsets = offsets,
        )
        val results = loadUntilEnd(source)
        assertEquals(listOf(400602, 400603), results.map { it.subjectInfo.subjectId })
        assertEquals(listOf(0, 20), offsets)
    }

    @Test
    fun `offset advances by requested load size instead of visible result count`() = runTest {
        val offsets = mutableListOf<Int>()
        val source = source(
            SearchSort.RANK,
            listOf(page(1, 1, count = 5), page(400602, 36360), "{\"items\":[]}"),
            requestOffsets = offsets,
            loadSize = 5,
        )
        val results = loadUntilEnd(source, loadSize = 5)
        assertEquals(listOf(400602), results.map { it.subjectInfo.subjectId })
        assertEquals(listOf(0, 5, 10), offsets)
    }

    private suspend fun loadUntilEnd(
        source: PagingSource<Int, BatchSubjectDetails>,
        loadSize: Int = 20,
    ): List<BatchSubjectDetails> {
        val results = mutableListOf<BatchSubjectDetails>()
        var result = assertIs<Page>(source.load(refresh(loadSize)))
        while (true) {
            results += result.data
            val nextKey = result.nextKey ?: return results
            result = assertIs<Page>(source.load(append(nextKey, loadSize)))
        }
    }

    private fun source(
        sort: SearchSort,
        pages: List<String>,
        ignoreDone: Boolean = false,
        requestOffsets: MutableList<Int> = mutableListOf(),
        loadSize: Int = 20,
    ): PagingSource<Int, BatchSubjectDetails> {
        var pageIndex = 0
        val engine = MockEngine { request ->
            assertEquals("/v2/subjects/search", request.url.encodedPath)
            assertEquals(">=2023-01-01,<2024-01-01", request.url.parameters["airDates"])
            assertEquals(if (sort == SearchSort.RANK) "ratingDesc" else "relevance", request.url.parameters["sortBy"])
            assertEquals((pageIndex * loadSize).toString(), request.url.parameters["offset"])
            assertEquals(loadSize.toString(), request.url.parameters["limit"])
            requestOffsets += request.url.parameters["offset"]!!.toInt()
            respond(pages[pageIndex++], headers = headersOf("Content-Type", "application/json"))
        }
        val api = SubjectsAniApi(baseUrl = "http://test", httpClientEngine = engine)
        val invoker = object : ApiInvoker<SubjectsAniApi> {
            override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R = action(api)
        }
        val repository = SubjectSearchRepository(AniSubjectSearchService(invoker), Collections())
        return repository.SubjectSearchPagingSource({ ignoreDone }, SubjectSearchQuery("", year = 2023, sort = sort))
    }

    private fun page(id: Int, ratingTotal: Int, count: Int = 1): String {
        val items = (0 until count).joinToString(",") { index ->
            """
                {"id":${id + index},"name":"subject","nameCn":"","summary":"","imageLarge":"",
                "nsfw":false,"airDate":"2023-09-29","score":"10","rank":0,"ratingTotal":$ratingTotal,
                "favorite":{"wish":0,"done":0,"doing":0,"onHold":0,"dropped":0},"tags":[],
                "mainEpisodeCount":12,"lightRelatedPersonInfoList":[]}
            """.trimIndent()
        }
        return "{\"items\":[$items]}"
    }

    private fun refresh(loadSize: Int = 20) =
        PagingSource.LoadParams.Refresh(key = 0, loadSize = loadSize, placeholdersEnabled = false)
    private fun append(key: Int, loadSize: Int = 20) =
        PagingSource.LoadParams.Append(key = key, loadSize = loadSize, placeholdersEnabled = false)

    private class Collections : SubjectCollectionRepository() {
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
        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            flowOf(listOf(400602))

        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            unsupported()

        override suspend fun performBangumiFullSync() = unsupported()
        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by SubjectSearchPagingSourceTest")
    }
}

private typealias Page = PagingSource.LoadResult.Page<Int, BatchSubjectDetails>
