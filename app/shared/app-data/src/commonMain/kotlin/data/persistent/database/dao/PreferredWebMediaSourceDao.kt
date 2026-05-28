/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferredWebMediaSourceDao {
    @Upsert
    suspend fun setPreferredMediaSource(preferredWebMediaSource: PreferredWebMediaSource)

    @Query("select mediaSourceId from preferred_web_media_source where subjectId = :subjectId")
    fun getPreferredMediaSourceId(subjectId: Int): Flow<String?>

    @Query("delete from preferred_web_media_source where subjectId = :subjectId")
    suspend fun deletePreferredMediaSource(subjectId: Int)
}

@Entity(
    tableName = "preferred_web_media_source",
    foreignKeys = [
        ForeignKey(
            entity = SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subjectId"], unique = true),
    ],
)
data class PreferredWebMediaSource(
    @PrimaryKey val subjectId: Int,
    val mediaSourceId: String,
)