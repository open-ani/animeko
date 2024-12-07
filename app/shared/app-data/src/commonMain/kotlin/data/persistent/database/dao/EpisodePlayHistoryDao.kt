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
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(
    tableName = "episode_play_history",
)
data class EpisodePlayHistoryEntity(
    @PrimaryKey
    val episodeId: Int,
    val positionMillis: Long
)

@Dao
interface EpisodePlayHistoryDao {
    @Upsert
    suspend fun upsert(item: EpisodePlayHistoryEntity)

    @Query(
        """
        SELECT * FROM episode_play_history 
        WHERE episodeId = :episodeId 
        LIMIT 1
    """,
    )
    suspend fun findByEpisodeId(episodeId: Int): EpisodePlayHistoryEntity?

    @Query(
        """
        DELETE FROM episode_play_history WHERE episodeId = :episodeId
    """,
    )
    suspend fun deleteByEpisodeId(episodeId: Int)

}