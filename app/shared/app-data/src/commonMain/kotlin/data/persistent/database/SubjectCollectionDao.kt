/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.persistent.ProtoConverters
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis

private const val TABLE_NAME = "subject_collection"

/**
 * @see SubjectInfo
 */
@Entity(
    tableName = TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
        ),
    ],
)
@TypeConverters(ProtoConverters::class)
data class SubjectCollectionEntity(
    @PrimaryKey val subjectId: Int,
    val type: UnifiedCollectionType,
    val created: Long = currentTimeMillis(),
    val lastUpdated: Long = currentTimeMillis(),
)

@Dao
interface SubjectCollectionDao {
    @Query("""SELECT * FROM $TABLE_NAME WHERE subjectId = :subjectId""")
    suspend fun get(subjectId: Int): SubjectCollectionEntity

    @Upsert
    suspend fun upsert(item: SubjectCollectionEntity)

    @Upsert
    suspend fun upsert(item: List<SubjectCollectionEntity>)

    @Query("""UPDATE $TABLE_NAME SET type = :type, lastUpdated = :lastUpdated WHERE subjectId = :subjectId""")
    suspend fun updateType(
        subjectId: Int,
        type: UnifiedCollectionType,
        lastUpdated: Long = currentTimeMillis(),
    )

    @Query("""DELETE FROM $TABLE_NAME WHERE subjectId = :subjectId""")
    suspend fun delete(subjectId: Int)

    /**
     * Retrieves a paginated list of `SubjectCollectionEntity` items, optionally filtered by type.
     *
     * @param type Optional filter for the `type` of items. If `null`, all items are retrieved.
     * @param limit Specifies the maximum number of items to retrieve.
     * @param offset Defines the starting position within the result set, allowing for pagination.
     * @return A `Flow` of a list of `SubjectCollectionEntity` items.
     */
    @Query(
        """
        select * from $TABLE_NAME 
        where (:type IS NULL OR type = :type)
        order by lastUpdated desc
        limit :limit offset :offset
        """,
    )
    fun getFlow(
        type: UnifiedCollectionType? = null,
        limit: Int,
        offset: Int,
    ): Flow<List<SubjectCollectionEntity>>

    @Query("""SELECT * FROM $TABLE_NAME WHERE subjectId = :subjectId""")
    fun findById(subjectId: Int): Flow<SubjectCollectionEntity?>

    @Query("""SELECT lastUpdated FROM $TABLE_NAME ORDER BY lastUpdated DESC LIMIT 1""")
    suspend fun lastUpdated(): Long
}