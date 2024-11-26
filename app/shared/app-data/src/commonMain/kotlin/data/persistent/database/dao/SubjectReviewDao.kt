/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import me.him188.ani.app.data.persistent.database.entity.SubjectReviewEntity

@Dao
interface SubjectReviewDao {
    @Upsert
    suspend fun upsert(item: SubjectReviewEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<SubjectReviewEntity>)

    @Query(
        """
        SELECT * FROM subject_review 
        WHERE subjectId = :subjectId
        ORDER BY updatedAt DESC
        """,
    )
    fun filterBySubjectIdPager(
        subjectId: Int,
    ): PagingSource<Int, SubjectReviewEntity>
}