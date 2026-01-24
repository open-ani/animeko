/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.app.data.persistent.database.converters.DurationConverter
import me.him188.ani.app.data.persistent.database.converters.InstantConverter
import me.him188.ani.app.data.persistent.database.converters.PackedDateConverter
import me.him188.ani.app.data.persistent.database.dao.DanmakuDao
import me.him188.ani.app.data.persistent.database.dao.DanmakuEntity
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.EpisodeCommentDao
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.data.persistent.database.dao.PreferredWebMediaSource
import me.him188.ani.app.data.persistent.database.dao.PreferredWebMediaSourceDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryDao
import me.him188.ani.app.data.persistent.database.dao.SearchHistoryEntity
import me.him188.ani.app.data.persistent.database.dao.SearchTagDao
import me.him188.ani.app.data.persistent.database.dao.SearchTagEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.dao.SubjectReviewDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.persistent.database.entity.CharacterActorEntity
import me.him188.ani.app.data.persistent.database.entity.CharacterEntity
import me.him188.ani.app.data.persistent.database.entity.EpisodeCommentEntity
import me.him188.ani.app.data.persistent.database.entity.PersonEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectPersonRelationEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectReviewEntity
import me.him188.ani.utils.httpdownloader.DownloadState

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

        SubjectReviewEntity::class,
        EpisodeCommentEntity::class,

        WebSearchSubjectInfoEntity::class,
        WebSearchEpisodeInfoEntity::class,

        TorrentCacheInfoEntity::class,
        DownloadState::class,
        DanmakuEntity::class,

        PreferredWebMediaSource::class,
    ],
    version = 19,
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
        AutoMigration(from = 10, to = 11, spec = Migrations.Migration_10_11::class),
        AutoMigration(from = 11, to = 12, spec = Migrations.Migration_11_12::class),
        AutoMigration(from = 12, to = 13, spec = Migrations.Migration_12_13::class),
        AutoMigration(from = 13, to = 14, spec = Migrations.Migration_13_14::class),
        AutoMigration(from = 14, to = 15, spec = Migrations.Migration_14_15::class),
        // 15 to 16 is destructive
        AutoMigration(from = 16, to = 17, spec = Migrations.Migration_16_17::class),
        AutoMigration(from = 17, to = 18, spec = Migrations.Migration_17_18::class),
        AutoMigration(from = 18, to = 19, spec = Migrations.Migration_18_19::class),
    ],
    exportSchema = true,
)
@ConstructedBy(AniDatabaseConstructor::class)
@TypeConverters(
    PackedDateConverter::class,
    DurationConverter::class,
    InstantConverter::class,
    EpisodeSortConverter::class,
    DanmakuServiceIdConverter::class,
)
abstract class AniDatabase : RoomDatabase() {
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun searchTag(): SearchTagDao
    abstract fun subjectCollection(): SubjectCollectionDao
    abstract fun episodeCollection(): EpisodeCollectionDao

    /**
     * @since 4.0.0-alpha04
     */
    abstract fun subjectRelations(): SubjectRelationsDao

    /**
     * @since 4.1.0-alpha02
     */
    abstract fun subjectReviews(): SubjectReviewDao

    /**
     * @since 4.1.0-alpha02
     */
    abstract fun episodeCommentDao(): EpisodeCommentDao

    /**
     * @since 4.1.0-alpha03
     */
    abstract fun webSearchSubjectInfoDao(): WebSearchSubjectInfoDao
    abstract fun webSearchEpisodeInfoDao(): WebSearchEpisodeInfoDao

    /**
     * @since 5.1.0
     */
    abstract fun torrentCacheInfoDao(): TorrentCacheInfoDao
    abstract fun httpCacheDownloadStateDao(): HttpCacheDownloadStateDao

    /**
     * @since 5.3.0
     */
    abstract fun danmakuDao(): DanmakuDao
    abstract fun preferredWebMediaSourceDao(): PreferredWebMediaSourceDao
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

    /**
     * Added [SubjectReviewEntity], [SubjectReviewDao],
     * [EpisodeCommentDao], [EpisodeCommentEntity]
     *
     * @since 4.1.0-alpha02
     */
    class Migration_10_11 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [EpisodeCollectionEntity.sortNumber]
     * @since 4.1.0-alpha02
     */
    class Migration_11_12 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [WebSearchSubjectInfoEntity], [WebSearchEpisodeInfoEntity],
     * [WebSearchSubjectInfoDao], [WebSearchEpisodeInfoDao]
     * @since 4.1.0-alpha03
     */
    class Migration_12_13 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * [SubjectCollectionEntity.lastUpdated] now defaults to `0` instead of `CURRENT_TIMESTAMP`.
     *
     * @since 4.9.0-alpha03
     */
    class Migration_13_14 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [SubjectCollectionEntity.relations].
     *
     * @since 5.0.0
     */
    class Migration_14_15 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [TorrentCacheInfoEntity] and [DownloadState].
     *
     * @since 5.2.0
     */
    class Migration_16_17 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [DanmakuEntity].
     *
     * @since 5.3.0
     */
    class Migration_17_18 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }

    /**
     * Added [PreferredWebMediaSource].
     *
     * @since 5.3.0
     */
    class Migration_18_19 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
        }
    }
}