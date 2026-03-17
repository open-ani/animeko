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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.repository.Repository
import org.koin.core.component.KoinComponent

class SubjectSearchHistoryRepository(
    private val searchHistory: SearchHistoryDao,
    private val searchTag: SearchTagDao,
) : Repository(), KoinComponent {
    suspend fun addHistory(content: String) = withContext(defaultDispatcher) {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty()) {
            return@withContext
        }

        searchHistory.insert(SearchHistoryEntity(content = normalizedContent))
    }

    suspend fun removeHistory(content: String) = withContext(defaultDispatcher) {
        searchHistory.deleteByContent(content)
    }

    fun getHistoryPager(): Flow<PagingData<String>> {
        return Pager(
            config = defaultPagingConfig,
            pagingSourceFactory = { searchHistory.allPager() },
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
