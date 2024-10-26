/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis


@Dao
interface EpisodeCollectionDao {
    @Query("""SELECT * FROM episode_collection JOIN episode WHERE episodeId = :episodeId LIMIT 1""")
    fun getByEpisodeId(episodeId: Int): PagingSource<Int, EpisodeCollectionDetail>

    @Query(
        """
        SELECT * FROM episode_collection JOIN episode
        WHERE subjectId = :subjectId
        AND (:episodeType IS NULL OR episode.type = :episodeType)
        """
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeType: EpisodeType?,
    ): Flow<List<EpisodeCollectionDetail>>

    @Query("""SELECT * FROM episode_collection JOIN episode WHERE subjectId = :subjectId""")
    fun filterBySubjectIdPaging(subjectId: Int): PagingSource<Int, EpisodeCollectionDetail>

    @Upsert
    suspend fun upsert(item: EpisodeCollectionEntity)

    @Upsert
    suspend fun upsert(item: List<EpisodeCollectionEntity>)

    @Query("""select * from episode_collection""")
    fun getFlow(): Flow<List<EpisodeCollectionDetail>>

    @Query("""SELECT lastUpdated FROM episode_collection ORDER BY lastUpdated DESC LIMIT 1""")
    suspend fun lastUpdated(): Long
}

@Entity(
    tableName = "episode_collection",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
        ),
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
        ),
    ],
    indices = [
        androidx.room.Index(value = ["subjectId", "episodeId"], unique = true),
    ]
)
data class EpisodeCollectionEntity(
    val subjectId: Int,
    @PrimaryKey val episodeId: Int,
    val type: UnifiedCollectionType,
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    val lastUpdated: Long = currentTimeMillis(),
)

data class EpisodeCollectionDetail(
    val subjectId: Int,
    val episodeId: Int,
    val type: UnifiedCollectionType,
    val lastUpdated: Long = currentTimeMillis(),
    @Embedded
    val episodeInfo: EpisodeInfo,
)
