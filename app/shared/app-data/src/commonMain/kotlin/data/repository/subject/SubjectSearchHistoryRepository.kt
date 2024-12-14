/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
        searchHistory.insert(SearchHistoryEntity(content = content))
    }

    suspend fun removeHistory(content: String) = withContext(defaultDispatcher) {
        searchHistory.deleteByContent(content)
    }

    fun getHistoryFlow(): Flow<List<String>> {
        return searchHistory.all().map { list -> list.map { it.content } }
            .flowOn(defaultDispatcher)
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