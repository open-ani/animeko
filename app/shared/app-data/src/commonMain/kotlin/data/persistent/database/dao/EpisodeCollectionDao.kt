/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.persistent.database.converters.PackedDateConverter
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


@Entity(
    tableName = "episode_collection",
    foreignKeys = [
        ForeignKey(
            entity = SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subjectId", "episodeId"], unique = true),
        Index(value = ["sortNumber", "sort"], orders = [Index.Order.ASC, Index.Order.ASC]),
//        Index(
//            value = ["subjectId", "sort"],
//            unique = true,
//            orders = [Index.Order.ASC]
//        ),
    ],
)
@TypeConverters(PackedDateConverter::class)
data class EpisodeCollectionEntity(
    val subjectId: Int,
    @PrimaryKey val episodeId: Int,

    val episodeType: EpisodeType?,
    val name: String,
    val nameCn: String,
    val airDate: PackedDate,
    @Deprecated("Not available anymore")
    val comment: Int,
    val desc: String,
    val sort: EpisodeSort,
    /**
     * [EpisodeSort.number]. 用于排序.
     */
    @ColumnInfo(defaultValue = "3.4028235e38") // Float.MAX_VALUE
    val sortNumber: Float, // see #1256
    val ep: EpisodeSort? = null,

    /** TMDB 剧照 (宽 300px) 的公开直链, 见 [me.him188.ani.app.data.models.episode.EpisodeInfo.imageMedium]. */
    val imageMedium: String? = null,

    /** TMDB 原尺寸剧照的公开直链, 见 [me.him188.ani.app.data.models.episode.EpisodeInfo.imageLarge]. */
    val imageLarge: String? = null,

    val selfCollectionType: UnifiedCollectionType,

    /**
     * 最后从服务器获取的时间
     */
    val lastFetched: Long,
)


@Dao
interface EpisodeCollectionDao {
    @Query(
        """
        SELECT * FROM episode_collection 
        WHERE episodeId = :episodeId 
        ORDER BY sort DESC
        LIMIT 1
        """,
    )
    fun findByEpisodeId(episodeId: Int): Flow<EpisodeCollectionEntity?>


    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        AND (episodeType = :episodeType)
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeType: EpisodeType,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        AND (episodeType IN (:episodeTypes))
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeTypes: List<EpisodeType>,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT episodeId FROM episode_collection
        WHERE subjectId = :subjectId
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun listIdBySubjectId(
        subjectId: Int,
    ): Flow<List<Int>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId 
        ORDER BY sortNumber ASC, sort ASC""",
    )
    fun filterBySubjectIdPaging(subjectId: Int): PagingSource<Int, EpisodeCollectionEntity>


    @Query("SELECT * FROM episode_collection WHERE episodeId IN (:episodeIds)")
    fun filterByEpisodeIds(episodeIds: Collection<Int>): Flow<List<EpisodeCollectionEntity>>

    @Upsert
    suspend fun upsertEntities(item: EpisodeCollectionEntity)

    @Upsert
    suspend fun upsertEntities(items: List<EpisodeCollectionEntity>)

    /**
     * 把服务端返回的剧集写进本地. 用户本地改过但还没同步上去的看过状态 (见 [EpisodeCollectionPendingOpEntity])
     * 会在写入后按待同步操作重新盖回去, 否则一次刷新就把离线时的标记冲掉了.
     */
    @Transaction
    suspend fun upsert(item: EpisodeCollectionEntity) {
        upsertEntities(item)
        reapplyPendingCollectionTypes()
    }

    @Transaction
    suspend fun upsert(item: List<EpisodeCollectionEntity>) {
        upsertEntities(item)
        reapplyPendingCollectionTypes()
    }

    @Query(
        """
        UPDATE episode_collection SET selfCollectionType = (
            SELECT p.collectionType FROM episode_collection_pending_op p
            WHERE p.episodeId = episode_collection.episodeId
            ORDER BY p.id DESC LIMIT 1
        )
        WHERE episodeId IN (SELECT episodeId FROM episode_collection_pending_op)
        """,
    )
    suspend fun reapplyPendingCollectionTypes()

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


    @Query("""select * from episode_collection ORDER BY sortNumber ASC, sort ASC""")
    fun all(): Flow<List<EpisodeCollectionEntity>>


    @Query(
        """
        SELECT lastFetched FROM episode_collection 
        WHERE subjectId = :subjectId
        ORDER BY lastFetched DESC LIMIT 1""",
    )
    suspend fun lastFetched(subjectId: Int): Long

    @Query(
        """
        DELETE FROM episode_collection 
        WHERE subjectId = :subjectId
        """,
    )
    suspend fun deleteAllBySubjectId(subjectId: Int)

    @Query(
        """
        DELETE FROM episode_collection 
        WHERE subjectId = :subjectId AND episodeId IN (:episodeIds)
        """,
    )
    suspend fun deleteAllByEpisodeIds(subjectId: Int, episodeIds: List<Int>)
}

fun EpisodeCollectionDao.filterBySubjectId(
    subjectId: Int,
    episodeType: EpisodeType? = null,
) = if (episodeType == null) {
    filterBySubjectId(subjectId)
} else {
    filterBySubjectId(subjectId, episodeType)
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
