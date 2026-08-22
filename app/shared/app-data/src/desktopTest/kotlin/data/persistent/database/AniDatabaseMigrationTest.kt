/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AniDatabase 迁移测试 (infra#10, P0#18).
 *
 * 生产迁移链 (CommonKoinModule): 1..15 destructive, 16 起走
 * AutoMigration 16→17→18→19, 手动 [MIGRATION_19_20], AutoMigration 20→21→22.
 *
 * [MigrationTestHelper] 从 `schemas/<db fqn>/<version>.json` 建旧版本库,
 * runMigrationsAndValidate 会把迁移后的实际 schema 与目标版本 json 逐表逐列校验.
 */
class AniDatabaseMigrationTest {
    private fun createHelper(): MigrationTestHelper = MigrationTestHelper(
        schemaDirectoryPath = resolveSchemaDirectory(),
        databasePath = Files.createTempDirectory("ani-migration-test").resolve("test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = AniDatabase::class,
        databaseFactory = { AniDatabaseConstructor.initialize() },
    )

    @Test
    fun `MIG-01 v16建库经AutoMigration与手动19-20迁移到v21通过schema校验且关键表存在`() {
        val helper = createHelper()
        helper.createDatabase(16).use { connection ->
            connection.execSQL("INSERT INTO `search_history` (`content`) VALUES ('bocchi')")
        }
        helper.runMigrationsAndValidate(21, listOf(MIGRATION_19_20)).use { connection ->
            val tables = connection.tableNames()
            assertContains(tables, "subject_collection")
            assertContains(tables, "episode_collection")
            assertContains(tables, "episode_comment")
            assertContains(tables, "preferred_web_media_source")
            assertContains(tables, "playback_history_record")
            assertContains(tables, "playback_history_pending_op")

            connection.prepare("SELECT `content` FROM `search_history`").use { statement ->
                assertTrue(statement.step())
                assertEquals("bocchi", statement.getText(0))
                assertFalse(statement.step())
            }
        }
    }

    @Test
    fun `MIG-02 v19已有preferred_web_media_source行经手动19-20与AutoMigration到v21保留`() {
        val helper = createHelper()
        helper.createDatabase(19).use { connection ->
            connection.execSQL(
                "INSERT INTO `preferred_web_media_source` (`subjectId`, `mediaSourceId`) VALUES (42, 'web2')",
            )
            connection.execSQL(
                """
                INSERT INTO `episode_comment`
                    (`commentId`, `episodeId`, `parentCommentId`, `authorId`, `authorNickname`, `authorAvatarUrl`, `createdAt`, `content`)
                VALUES (1, 1, NULL, 1, 'nick', NULL, 0, 'stale')
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(21, listOf(MIGRATION_19_20)).use { connection ->
            connection.prepare("SELECT `subjectId`, `mediaSourceId` FROM `preferred_web_media_source`").use { statement ->
                assertTrue(statement.step())
                assertEquals(42, statement.getInt(0))
                assertEquals("web2", statement.getText(1))
                assertFalse(statement.step())
            }
            // PINNED: MIG-02 手动迁移 19→20 DROP 重建 episode_comment, 旧评论数据全部丢弃
            connection.prepare("SELECT COUNT(*) FROM `episode_comment`").use { statement ->
                assertTrue(statement.step())
                assertEquals(0, statement.getInt(0))
            }
        }
    }

    @Test
    fun `MIG-03 v20到v21的AutoMigration增加播放记录表`() {
        val helper = createHelper()
        helper.createDatabase(20).use { connection ->
            assertFalse(connection.tableNames().contains("playback_history_record"))
        }
        helper.runMigrationsAndValidate(21, emptyList()).use { connection ->
            val tables = connection.tableNames()
            assertContains(tables, "playback_history_record")
            assertContains(tables, "playback_history_pending_op")
        }
    }

    @Test
    fun `MIG-05 v21到v22的AutoMigration删除旧web搜索缓存表并新建session缓存表`() {
        val helper = createHelper()
        helper.createDatabase(21).use { connection ->
            val tables = connection.tableNames()
            assertContains(tables, "web_search_subject")
            assertContains(tables, "web_search_episode")
        }
        helper.runMigrationsAndValidate(22, emptyList()).use { connection ->
            val tables = connection.tableNames()
            // PINNED: MIG-05 旧的两张表被 @DeleteTable 删除, 其中的数据 (会话级缓存) 全部丢弃
            assertFalse(tables.contains("web_search_subject"))
            assertFalse(tables.contains("web_search_episode"))
            assertContains(tables, "web_search_session_cache")
        }
    }

    @Test
    fun `MIG-04 缺失手动19-20迁移时从v16迁移到v21失败`() {
        val helper = createHelper()
        helper.createDatabase(16).use {}
        val exception = assertFails {
            helper.runMigrationsAndValidate(21, emptyList())
        }
        assertContains(exception.message.orEmpty(), "A migration from 16 to 21 was required but not found")
    }

    private fun SQLiteConnection.tableNames(): Set<String> =
        prepare("SELECT `name` FROM sqlite_master WHERE `type` = 'table'").use { statement ->
            buildSet {
                while (statement.step()) {
                    add(statement.getText(0))
                }
            }
        }

    private fun resolveSchemaDirectory(): Path {
        val candidates = listOf(
            Paths.get("schemas"),
            Paths.get("app/shared/app-data/schemas"),
        )
        return candidates.firstOrNull {
            Files.isDirectory(it.resolve(AniDatabase::class.qualifiedName!!))
        }?.toAbsolutePath()
            ?: error(
                "Cannot locate Room schema directory. Tried ${candidates.map { it.toAbsolutePath() }} " +
                        "from working directory ${Paths.get("").toAbsolutePath()}",
            )
    }
}
