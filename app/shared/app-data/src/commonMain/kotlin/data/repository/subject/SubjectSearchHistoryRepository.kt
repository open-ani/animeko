/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingData.Companion.from
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.repository.Repository
import org.koin.core.component.KoinComponent

class SubjectSearchHistoryRepository(
    private val searchHistory: SearchHistoryDao?,
    private val searchTag: SearchTagDao?,
) : Repository(), KoinComponent {
    private val inMemoryHistory = MutableStateFlow<List<String>>(emptyList())

    suspend fun addHistory(content: String) = withContext(defaultDispatcher) {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty()) {
            return@withContext
        }

        searchHistory?.let {
            it.deleteByContent(normalizedContent)
            it.insert(SearchHistoryEntity(content = normalizedContent))
        }
            ?: inMemoryHistory.update { history -> listOf(normalizedContent) + history.filter { it != normalizedContent } }
    }

    suspend fun removeHistory(content: String) = withContext(defaultDispatcher) {
        searchHistory?.deleteByContent(content)
            ?: inMemoryHistory.update { history -> history.filter { it != content } }
    }

    fun getHistoryPager(): Flow<PagingData<String>> {
        val searchHistory = searchHistory ?: return inMemoryHistory.map { from(it) }.flowOn(defaultDispatcher)
        return Pager(
            config = defaultPagingConfig,
            pagingSourceFactory = {
                object : PagingSource<Int, String>() {
                    override fun getRefreshKey(state: PagingState<Int, String>): Int? = state.anchorPosition

                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
                        val offset = params.key ?: 0
                        val data = searchHistory.listPage(limit = params.loadSize, offset = offset)
                        return LoadResult.Page(
                            data = data,
                            prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                            nextKey = if (data.size < params.loadSize) null else offset + data.size,
                        )
                    }
                }
            },
        ).flow.flowOn(defaultDispatcher)
    }

//    suspend fun addTag(tag: SearchTagEntity) {
//        searchTag.insert(tag)
//    }
//
//    fun getTagFlow(): Flow<List<String>> {
//        return searchTag.getFlow().map { list -> list.map { it.content } }
//            .flowOn(defaultDispatcher)
//    }
//
//    suspend fun deleteTagByName(content: String) {
//        searchTag.deleteByName(content)
//    }
//
//    suspend fun increaseCountByName(content: String) {
//        searchTag.increaseCountByName(content)
//    }
//
//    suspend fun deleteTagById(id: Int) {
//        searchTag.deleteById(id)
//    }
//
//    suspend fun increaseCountById(id: Int) {
//        searchTag.increaseCountById(id)
//    }
}
