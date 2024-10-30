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
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.persistent.database.dao.SearchTagEntity
import org.koin.core.component.KoinComponent

interface SubjectSearchHistoryRepository {
    suspend fun addHistory(content: String)
    fun getHistoryFlow(): Flow<List<String>>
    suspend fun deleteHistoryBySeq(seq: Int)

    suspend fun addTag(tag: SearchTagEntity)
    fun getTagFlow(): Flow<List<String>>
    suspend fun deleteTagByName(content: String)
    suspend fun increaseCountByName(content: String)
    suspend fun deleteTagById(id: Int)
    suspend fun increaseCountById(id: Int)
}

class SubjectSearchHistoryRepositoryImpl(
    private val searchHistory: SearchHistoryDao,
    private val searchTag: SearchTagDao,
) : SubjectSearchHistoryRepository, KoinComponent {
    override suspend fun addHistory(content: String) {
        searchHistory.insert(SearchHistoryEntity(content = content))
    }

    override fun getHistoryFlow(): Flow<List<String>> {
        return searchHistory.all().map { list -> list.map { it.content } }
    }

    override suspend fun deleteHistoryBySeq(seq: Int) {
        searchHistory.deleteBySequence(seq)
    }

    override suspend fun addTag(tag: SearchTagEntity) {
        searchTag.insert(tag)
    }

    override fun getTagFlow(): Flow<List<String>> {
        return searchTag.getFlow().map { list -> list.map { it.content } }
    }

    override suspend fun deleteTagByName(content: String) {
        searchTag.deleteByName(content)
    }

    override suspend fun increaseCountByName(content: String) {
        searchTag.increaseCountByName(content)
    }

    override suspend fun deleteTagById(id: Int) {
        searchTag.deleteById(id)
    }

    override suspend fun increaseCountById(id: Int) {
        searchTag.increaseCountById(id)
    }
}