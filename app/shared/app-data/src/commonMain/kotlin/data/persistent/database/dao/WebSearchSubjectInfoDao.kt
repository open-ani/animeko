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
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(
    tableName = "web_search_subject",
    indices = [
        Index(value = ["mediaSourceId", "subjectName"], unique = true),
        Index(value = ["mediaSourceId"]),
    ],
    primaryKeys = ["mediaSourceId", "subjectName"],
)
data class WebSearchSubjectInfoEntity(
    val mediaSourceId: String,
    val subjectName: String,
    val internalId: String,
    val name: String,
    val fullUrl: String,
    val partialUrl: String,
)

@Dao
interface WebSearchSubjectInfoDao {

    @Upsert
    suspend fun upsert(item: WebSearchSubjectInfoEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<WebSearchSubjectInfoEntity>)

    @Query(
        """
        SELECT * FROM web_search_subject
        WHERE mediaSourceId = :mediaSourceId and subjectName = :subjectName
        """,
    )
    @Transaction
    suspend fun filterByMediaSourceIdAndSubjectName(
        mediaSourceId: String,
        subjectName: String,
    ): List<WebSearchSubjectInfoAndEpisodes>

    @Query(
        """
        DELETE FROM web_search_subject
        """,
    )
    suspend fun deleteAll()
}

data class WebSearchSubjectInfoAndEpisodes(
    @Embedded
    val webSubjectInfo: WebSearchSubjectInfoEntity,
    @Relation(
        entity = WebSearchEpisodeInfoEntity::class,
        parentColumn = "mediaSourceId",
        entityColumn = "mediaSourceId",
    )
    val webEpisodeInfos: List<WebSearchEpisodeInfoEntity>,
) 

