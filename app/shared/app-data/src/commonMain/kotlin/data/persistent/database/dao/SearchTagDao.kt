/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchTagDao {
    @Upsert
    suspend fun insert(item: SearchTagEntity)

    @Query("select * from `search_tag` order by useCount desc")
    fun getFlow(): Flow<List<SearchTagEntity>>

    @Query("update `search_tag` set `useCount` = `useCount` + 1 where `content`=:content")
    suspend fun increaseCountByName(content: String)

    @Query("delete from `search_tag` where `content`=:content")
    suspend fun deleteByName(content: String)

    @Query("update `search_tag` set `useCount` = `useCount` + 1 where `id`=:id")
    suspend fun increaseCountById(id: Int)

    @Query("delete from `search_tag` where `id`=:id")
    suspend fun deleteById(id: Int)
}

@Entity(tableName = "search_tag")
data class SearchTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    /**
     * 使用此 tag 搜索的次数，次数越高在搜索建议中排名越靠前
     */
    val useCount: Int = 1,
)