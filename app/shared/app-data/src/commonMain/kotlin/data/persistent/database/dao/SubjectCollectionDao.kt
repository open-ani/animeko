/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.compose.runtime.Immutable
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.schedule.AnimeRecurrence
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.persistent.database.ProtoConverters
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.hours

/**
 * @see SubjectInfo
 */
@Entity(
    tableName = "subject_collection",
    indices = [
        Index(value = ["lastUpdated"], unique = false, orders = [Index.Order.DESC]),
    ],
)
data class SubjectCollectionEntity(
    @PrimaryKey val subjectId: Int,

    // SubjectInfo
    val name: String,
    val nameCn: String,
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,
    /**
     * 会在获取剧集列表时使用, 用于验证缓存的剧集数目是否正确
     */
    val totalEpisodes: Int,
    val airDate: PackedDate,
    @all:TypeConverters(ProtoConverters.StringList::class)
    val aliases: List<String>,
    @all:TypeConverters(ProtoConverters.TagList::class)
    val tags: List<Tag>,
    @Embedded(prefix = "collection_stats_")
    val collectionStats: SubjectCollectionStats,
    @Embedded(prefix = "rating_")
    val ratingInfo: RatingInfo,
    val completeDate: PackedDate,
    // SubjectCollectionInfo

    @Embedded(prefix = "self_rating_")
    val selfRatingInfo: SelfRatingInfo,
    val collectionType: UnifiedCollectionType,

    /**
     * @since 4.1.0-alpha01
     */
    @Embedded(prefix = "recurrence_")
    val recurrence: AnimeRecurrence?,

    /**
     * @since 5.0.0
     */
    @Embedded(prefix = "relations_")
    val relations: SubjectRelations = SubjectRelations.Empty,

    /**
     * 此条目最后被修改的时间 (如修改收藏状态). 与服务器同步.
     */
    @ColumnInfo(defaultValue = "0")
    val lastUpdated: Long,
    /**
     * 此条目从 bangumi 服务器上查询到的时间. 用于判断是否需要自动刷新
     */
    @ColumnInfo(defaultValue = "0")
    val lastFetched: Long,
    @ColumnInfo(defaultValue = "0")
    val cachedStaffUpdated: Long,
    @ColumnInfo(defaultValue = "0")
    val cachedCharactersUpdated: Long,
)

@Immutable // don't change field name, stored in database
data class SubjectRelations(
    @ColumnInfo(defaultValue = "'[]'")
    @all:TypeConverters(ProtoConverters.IntList::class)
    val seriesMainSubjectIds: List<Int>,
    @ColumnInfo(defaultValue = "'[]'")
    @all:TypeConverters(ProtoConverters.StringList::class)
    val seriesMainSubjectNames: List<String>,
    @ColumnInfo(defaultValue = "'[]'")
    @all:TypeConverters(ProtoConverters.IntList::class)
    val sequelSubjects: List<Int>,
    @ColumnInfo(defaultValue = "'[]'")
    @all:TypeConverters(ProtoConverters.StringList::class)
    val sequelSubjectNames: List<String>,
) {
    companion object {
        val Empty = SubjectRelations(
            seriesMainSubjectIds = emptyList(),
            seriesMainSubjectNames = emptyList(),
            sequelSubjects = emptyList(),
            sequelSubjectNames = emptyList(),
        )
    }
}

@Dao
interface SubjectCollectionDao {
    @Upsert
    suspend fun upsert(item: SubjectCollectionEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<SubjectCollectionEntity>)

    @Query("""UPDATE subject_collection SET collectionType = :collectionType, lastUpdated = :lastUpdated WHERE subjectId = :subjectId""")
    suspend fun updateType(
        subjectId: Int,
        collectionType: UnifiedCollectionType,
        lastUpdated: Long = currentTimeMillis(),
    )

    @Query("""DELETE FROM subject_collection WHERE subjectId = :subjectId""")
    suspend fun delete(subjectId: Int)

    @Query("""DELETE FROM subject_collection WHERE collectionType = :type""")
    suspend fun deleteAll(type: UnifiedCollectionType)

    @Query("""DELETE FROM subject_collection""")
    suspend fun deleteAll()

    /**
     * Retrieves a paginated list of `SubjectCollectionEntity` items, optionally filtered by type.
     *
     * @param collectionTypes Optional filter for the `type` of items. If `null`, all items are retrieved.
     * @param limit Specifies the maximum number of items to retrieve.
     * @param offset Defines the starting position within the result set, allowing for pagination.
     * @return A `Flow` of a list of `SubjectCollectionEntity` items.
     */
    @Query(
        """
    SELECT * FROM subject_collection 
    WHERE collectionType IS NOT NULL 
    AND (collectionType IN (:collectionTypes))
    ORDER BY lastUpdated DESC
    LIMIT :limit
    OFFSET :offset
    """,
    )
    fun filterMostRecentUpdated(
        collectionTypes: List<UnifiedCollectionType>,
        limit: Int,
        offset: Int = 0,
    ): Flow<List<SubjectCollectionEntity>>

    @Query(
        """
    SELECT * FROM subject_collection 
    WHERE collectionType IS NOT NULL 
    ORDER BY lastUpdated DESC
    LIMIT :limit
    OFFSET :offset
    """,
    )
    fun mostRecentUpdated(
        limit: Int,
        offset: Int = 0,
    ): Flow<List<SubjectCollectionEntity>>

    /**
     * Retrieves a paginated list of `SubjectCollectionEntity` items, optionally filtered by type.
     *
     * @param collectionType Optional filter for the `type` of items. If `null`, all items are retrieved. If empty, no item will be returned.
     * @return A `Flow` of a list of `SubjectCollectionEntity` items.
     */
    @Query(
        """
        select * from subject_collection 
        where (collectionType is NOT NULL AND (:collectionType IS NULL OR collectionType = :collectionType))
        AND (:includeNsfw OR NOT nsfw)
        order by lastUpdated DESC, subjectId DESC
        """,
    )
    @Transaction
    fun filterByCollectionTypePaging(
        collectionType: UnifiedCollectionType? = null,
        includeNsfw: Boolean,
    ): PagingSource<Int, SubjectCollectionAndEpisodes>

    @Query("""SELECT * FROM subject_collection WHERE subjectId = :subjectId""")
    fun findById(subjectId: Int): Flow<SubjectCollectionEntity?>

    @Query("""SELECT * FROM subject_collection WHERE subjectId IN (:subjectIds)""")
    fun filterByIds(subjectIds: IntArray): Flow<List<SubjectCollectionEntity>>

    @Query(
        """
        SELECT sc.subjectId FROM subject_collection sc WHERE NOT EXISTS (
            SELECT ec.lastFetched FROM episode_collection ec 
            WHERE (ec.subjectId = sc.subjectId) 
                AND (CAST(unixepoch('now', 'subsecond') * 1000 AS int) - ec.lastFetched > :cacheExpiry)
        )
        """,
    )
    fun subjectIdsWithValidEpisodeCollection(cacheExpiry: Long = 1.hours.inWholeMilliseconds): Flow<List<Int>>

    @Query(
        """
        SELECT lastFetched FROM subject_collection 
        WHERE (:type IS NULL) OR (collectionType = :type)
        ORDER BY lastFetched DESC LIMIT 1
        """,
    )
    suspend fun lastFetched(type: UnifiedCollectionType?): Long

    @Query(
        """
    UPDATE subject_collection 
    SET 
        self_rating_score = COALESCE(:score, self_rating_score), 
        self_rating_comment = COALESCE(:comment, self_rating_comment), 
        self_rating_tags = COALESCE(:tags, self_rating_tags), 
        self_rating_isPrivate = COALESCE(:private, self_rating_isPrivate)
    WHERE subjectId = :subjectId
""",
    )
    suspend fun updateRating(subjectId: Int, score: Int?, comment: String?, tags: List<String>?, private: Boolean?)

    /**
     * 只包含保存在数据库的, 可能不完整
     */
    @Query("""SELECT COUNT(*) FROM subject_collection WHERE (collectionType is NOT NULL AND (:collectionType IS NULL OR collectionType = :collectionType))""")
    fun countCollected(collectionType: UnifiedCollectionType?): Flow<Int>

    @Query("""UPDATE subject_collection SET cachedStaffUpdated = :time, cachedCharactersUpdated = :time WHERE subjectId = :subjectId""")
    suspend fun updateCachedRelationsUpdated(subjectId: Int, time: Long = currentTimeMillis())

    @Query(
        """
        SELECT sc.subjectId FROM subject_collection sc
        WHERE collectionType IS NOT NULL
        AND (collectionType IN (:collectionTypes))
        """,
    )
    fun subjectIdsByCollectionType(collectionTypes: List<UnifiedCollectionType>): Flow<List<Int>>

    @Query(
        """
        SELECT sc.nameCn FROM subject_collection sc
        WHERE collectionType IS NOT NULL
        AND (collectionType IN (:collectionTypes))
        """,
    )
    fun subjectNamesCnByCollectionType(collectionTypes: List<UnifiedCollectionType>): Flow<List<String>>
}

suspend inline fun SubjectCollectionDao.deleteAll(type: UnifiedCollectionType?) {
    if (type == null) {
        deleteAll()
    } else {
        deleteAll(type)
    }
}

data class SubjectCollectionAndEpisodes(
    @Embedded
    val collection: SubjectCollectionEntity,
    @Relation(
        entity = EpisodeCollectionEntity::class,
        parentColumn = "subjectId",
        entityColumn = "subjectId",
    )
    val episodesOfAnyType: List<EpisodeCollectionEntity>,
) {
    override fun toString(): String {
        return "SubjectCollectionAndEpisodes(collection.nameCn=${collection.nameCn}, episodes.size=${episodesOfAnyType.size})"
    }
}

fun SubjectCollectionDao.filterMostRecentUpdated(
    collectionTypes: List<UnifiedCollectionType>?,
    limit: Int,
    offset: Int = 0,
): Flow<List<SubjectCollectionEntity>> = if (collectionTypes == null) {
    mostRecentUpdated(limit, offset)
} else {
    filterMostRecentUpdated(collectionTypes, limit, offset)
}

fun SubjectCollectionDao.filterMostRecentUpdated(
    collectionType: UnifiedCollectionType? = null,
    limit: Int,
): Flow<List<SubjectCollectionEntity>> = filterMostRecentUpdated(listOfNotNull(collectionType), limit)
