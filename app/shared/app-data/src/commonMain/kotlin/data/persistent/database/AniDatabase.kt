/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import me.him188.ani.app.data.persistent.ProtoConverters
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.persistent.database.eneity.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.eneity.SearchTagEntity

@Database(
    entities = [
        SearchHistoryEntity::class,
        SearchTagEntity::class,
        SubjectCollectionEntity::class,
        EpisodeCollectionEntity::class,
    ],
    version = 2,
)
@ConstructedBy(AniDatabaseConstructor::class)
@TypeConverters(ProtoConverters::class)
abstract class AniDatabase : RoomDatabase() {
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun searchTag(): SearchTagDao
    abstract fun subjectCollection(): SubjectCollectionDao
    abstract fun episodeCollection(): EpisodeCollectionDao
}

expect object AniDatabaseConstructor : RoomDatabaseConstructor<AniDatabase> {
    override fun initialize(): AniDatabase
}
