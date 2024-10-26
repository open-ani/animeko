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
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.repository.SubjectInfo
import me.him188.ani.datasources.api.PackedDate

private const val TABLE_NAME = "subject"


@Dao
interface SubjectDao {
    @Query("""SELECT * FROM $TABLE_NAME WHERE id = :id""")
    fun find(id: Int): Flow<SubjectEntity?>

    @Upsert(entity = SubjectEntity::class)
    suspend fun upsert(item: SubjectEntity)

    @Upsert(entity = SubjectEntity::class)
    suspend fun upsert(item: List<SubjectEntity>)

    @Query("""select * from $TABLE_NAME""")
    fun all(): Flow<List<SubjectEntity>>
}

/**
 * @see SubjectInfo
 */
@Entity(tableName = TABLE_NAME)
data class SubjectEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val nameCn: String,
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,
    val totalEpisodes: Int,
    val airDateString: String?,
    val aliases: List<String>,
    val tags: List<Tag>,
    @Embedded(prefix = "collection_stats_")
    val collectionStats: SubjectCollectionStats,
    @Embedded(prefix = "rating_")
    val ratingInfo: RatingInfo,
    val completionDate: PackedDate,
)
//
//fun SubjectInfo.toEntity(): SubjectInfoEntity {
//    return SubjectInfoEntity(
//        subjectId = id,
//        name = name,
//        nameCn = nameCn,
//        summary = summary,
//        nsfw = nsfw,
//        imageLarge = imageLarge,
//        totalEpisodes = totalEpisodes,
//        airDateString = airDateString,
//        aliases = aliases,
//        collectionStats = collection,
//        ratingInfo = ratingInfo,
//    )
//}