/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackHistoryDaoTest {
    private fun runDatabaseTest(block: suspend (PlaybackHistoryDao) -> Unit) = runBlocking {
        val database: AniDatabase = createTestAniDatabase()
        try {
            block(database.playbackHistoryDao())
        } finally {
            database.close()
        }
    }

    private fun record(episodeId: Int, updatedAtMillis: Long, deletedAtMillis: Long? = null) =
        PlaybackHistoryRecordEntity(
            episodeId = episodeId,
            positionMillis = 1_000,
            updatedAtMillis = updatedAtMillis,
            deletedAtMillis = deletedAtMillis,
        )

    @Test
    fun `countAllRecords includes deleted records`() = runDatabaseTest { dao ->
        assertEquals(0, dao.countAllRecords())

        dao.upsertRecords(
            listOf(
                record(1, updatedAtMillis = 100),
                record(2, updatedAtMillis = 200, deletedAtMillis = 300),
            ),
        )

        assertEquals(2, dao.countAllRecords())
    }

    @Test
    fun `getEarliestUpdatedAtMillis ignores records without time`() = runDatabaseTest { dao ->
        assertNull(dao.getEarliestUpdatedAtMillis())

        dao.upsertRecords(
            listOf(
                record(1, updatedAtMillis = 0, deletedAtMillis = 50),
                record(2, updatedAtMillis = 300),
                record(3, updatedAtMillis = 200, deletedAtMillis = 400),
            ),
        )

        assertEquals(200, dao.getEarliestUpdatedAtMillis())
    }
}
