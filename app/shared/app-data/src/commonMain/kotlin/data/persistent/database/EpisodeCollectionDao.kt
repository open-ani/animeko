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
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis


@Entity(
    tableName = "episode_collection",
    foreignKeys = [
        ForeignKey(
            entity = SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
        ),
    ],
    primaryKeys = ["episodeId"],
    indices = [
        Index(value = ["subjectId", "episodeId"], unique = true),
//        Index(
//            value = ["subjectId", "sort"],
//            unique = true,
//            orders = [Index.Order.ASC]
//        ),
    ],
)
data class EpisodeCollectionEntity(
    val subjectId: Int,
    val episodeId: Int,

    val episodeType: EpisodeType?,
    val name: String,
    val nameCn: String,
    val airDate: PackedDate,
    val comment: Int,
    val desc: String,
    val sort: EpisodeSort,
    val ep: EpisodeSort? = null,

    val selfCollectionType: UnifiedCollectionType,
    val lastUpdated: Long = currentTimeMillis(),
)


@Dao
interface EpisodeCollectionDao {
    @Query("""SELECT * FROM episode_collection WHERE episodeId = :episodeId LIMIT 1""")
    fun findByEpisodeId(episodeId: Int): Flow<EpisodeCollectionEntity?>


    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        AND (:episodeType IS NULL OR episodeType = :episodeType)
        ORDER BY sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeType: EpisodeType?,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId 
        ORDER BY sort ASC""",
    )
    fun filterBySubjectIdPaging(subjectId: Int): PagingSource<Int, EpisodeCollectionEntity>


    @Upsert
    suspend fun upsert(item: EpisodeCollectionEntity)

    @Upsert
    suspend fun upsert(item: List<EpisodeCollectionEntity>)

    @Query("""UPDATE episode_collection SET selfCollectionType = :type WHERE subjectId = :subjectId AND episodeId = :episodeId""")
    suspend fun updateSelfCollectionType(
        subjectId: Int,
        episodeId: Int,
        type: UnifiedCollectionType,
    )

    @Query("""UPDATE episode_collection SET selfCollectionType = :type WHERE subjectId = :subjectId""")
    suspend fun setAllEpisodesWatched(
        subjectId: Int,
        type: UnifiedCollectionType = UnifiedCollectionType.DONE,
    )


    @Query("""select * from episode_collection ORDER BY sort ASC""")
    fun all(): Flow<List<EpisodeCollectionEntity>>


    @Query("""SELECT lastUpdated FROM episode_collection ORDER BY lastUpdated DESC LIMIT 1""")
    suspend fun lastUpdated(): Long
}

//@Entity(
//    tableName = "episode_collection",
//    foreignKeys = [
//        ForeignKey(
//            entity = SubjectEntity::class,
//            parentColumns = ["id"],
//            childColumns = ["subjectId"],
//        ),
//        ForeignKey(
//            entity = EpEn::class,
//            parentColumns = ["id"],
//            childColumns = ["episodeId"],
//        ),
//    ],
//    indices = [
//        Index(value = ["subjectId", "episodeId"], unique = true),
//    ]
//)
//data class EpisodeCollectionEntity(
//    val subjectId: Int,
//    @PrimaryKey val episodeId: Int,
//    val type: UnifiedCollectionType,
//    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
//    val lastUpdated: Long = currentTimeMillis(),
//)
