/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.persistent.database.dao.SearchTagEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.entity.CharacterActorEntity
import me.him188.ani.app.data.persistent.database.entity.CharacterEntity
import me.him188.ani.app.data.persistent.database.entity.PersonEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectPersonRelationEntity

@Database(
    entities = [
        SearchHistoryEntity::class,
        SearchTagEntity::class,
        SubjectCollectionEntity::class,
        EpisodeCollectionEntity::class,

        PersonEntity::class, // 4.0.0-alpha04
        SubjectPersonRelationEntity::class, // 4.0.0-alpha04

        CharacterEntity::class, // 4.0.0-alpha04
        SubjectCharacterRelationEntity::class, // 4.0.0-alpha04
        CharacterActorEntity::class, // 4.0.0-alpha04
    ],
    version = 10,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migrations.Migration_1_2::class),
        AutoMigration(from = 2, to = 3, spec = Migrations.Migration_2_3::class),
        AutoMigration(from = 3, to = 4, spec = Migrations.Migration_3_4::class),
        AutoMigration(from = 4, to = 5, spec = Migrations.Migration_4_5::class),
        AutoMigration(from = 5, to = 6, spec = Migrations.Migration_5_6::class),
        AutoMigration(from = 6, to = 7, spec = Migrations.Migration_6_7::class),
        AutoMigration(from = 7, to = 8, spec = Migrations.Migration_7_8::class),
        AutoMigration(from = 8, to = 9, spec = Migrations.Migration_8_9::class),
        AutoMigration(from = 9, to = 10, spec = Migrations.Migration_9_10::class),
    ],
)
@ConstructedBy(AniDatabaseConstructor::class)
@TypeConverters(ProtoConverters::class, EpisodeSortConverter::class)
abstract class AniDatabase : RoomDatabase() {
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun searchTag(): SearchTagDao
    abstract fun subjectCollection(): SubjectCollectionDao
    abstract fun episodeCollection(): EpisodeCollectionDao

    /**
     * @since 4.0.0-alpha04
     */
    abstract fun subjectRelations(): SubjectRelationsDao
}

expect object AniDatabaseConstructor : RoomDatabaseConstructor<AniDatabase> {
    override fun initialize(): AniDatabase
}

@Suppress("ClassName")
internal object Migrations {

    /**
     * 只增加了新的表
     *
     * @since 4.0.0-alpha03
     */
    class Migration_1_2 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * @since 4.0.0-alpha03
     */
    class Migration_2_3 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `distinct_content` ON `search_history`(`content`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `sequence_desc` ON `search_history`(`sequence` DESC)")
        }
    }

    /**
     * 增加了以下表:
     *
     * - [PersonEntity]
     * - [SubjectPersonRelationEntity]
     *
     * - [CharacterEntity]
     * - [SubjectCharacterRelationEntity]
     * - [CharacterActorEntity]
     *
     * DAO:
     * - [SubjectRelationsDao]
     *
     * @since 4.0.0-alpha04
     */
    class Migration_3_4 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * - [CharacterActorEntity] 改名
     *
     * @since 4.0.0-alpha04
     */
    @RenameTable("related_character", "character_actor")
    class Migration_4_5 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * - Added [SubjectCollectionEntity.cachedCharactersUpdated]
     * - Added [SubjectCollectionEntity.cachedStaffUpdated]
     *
     * @since 4.0.0-alpha04
     */
    class Migration_5_6 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * - Removed [SubjectCollectionEntity].`_index`. Primary key changed to [SubjectCollectionEntity.subjectId].
     * - Removed [SubjectCollectionEntity] index `Index(value = ["subjectId"], unique = true),`
     * - [SubjectCollectionDao.filterByCollectionTypePaging] 使用 [SubjectCollectionEntity.lastUpdated] 排序.
     * @since 4.0.0-beta01
     */
    @DeleteColumn("subject_collection", "_index")
    class Migration_6_7 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [SubjectCollectionEntity.lastFetched]
     * @since 4.0.0-beta03
     */
    class Migration_7_8 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Renamed [EpisodeCollectionEntity].`lastUpdated` to [EpisodeCollectionEntity.lastFetched]
     * @since 4.0.0-beta04
     */
    @RenameColumn("episode_collection", "lastUpdated", "lastFetched")
    class Migration_8_9 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [SubjectCollectionEntity.recurrence]
     * @since 4.1.0-alpha01
     */
    class Migration_9_10 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }
}