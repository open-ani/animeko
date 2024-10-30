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
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Upsert
    suspend fun insert(item: SearchHistoryEntity)

    @Query("delete from `search_history` where `sequence`=:sequence")
    suspend fun deleteBySequence(sequence: Int)

    @Query("select * from `search_history` order by sequence desc")
    fun all(): Flow<List<SearchHistoryEntity>>
}

@Entity(
    tableName = "search_history",
    indices = [
        Index(
            value = ["content"],
            name = "distinct_content",
            unique = true,
        ),
        Index(
            value = ["sequence"],
            name = "sequence_desc",
            orders = [Index.Order.DESC],
        ),
    ],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Int = 0,
    val content: String
)