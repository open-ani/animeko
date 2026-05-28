/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Transaction
import androidx.room3.Upsert
import me.him188.ani.datasources.api.EpisodeSort

@Entity(
    tableName = "web_search_episode",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["channel", "name", "parentId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = WebSearchSubjectInfoEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WebSearchEpisodeInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: String?,
    val name: String,
    val episodeSortOrEp: EpisodeSort?,
    val playUrl: String,
    val parentId: Long,
)

@Dao
interface WebSearchEpisodeInfoDao {
    @Upsert
    suspend fun upsert(item: WebSearchEpisodeInfoEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<WebSearchEpisodeInfoEntity>)
}