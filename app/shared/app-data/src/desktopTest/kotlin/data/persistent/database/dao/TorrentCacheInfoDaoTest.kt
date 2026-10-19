/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TorrentCacheInfoDaoTest {
    private fun runDatabaseTest(block: suspend (TorrentCacheInfoDao) -> Unit) = runBlocking {
        val database: AniDatabase = createTestAniDatabase()
        try {
            block(database.torrentCacheInfoDao())
        } finally {
            database.close()
        }
    }

    private fun entity(mediaId: String, torrentData: Byte, relativeDir: String) = TorrentCacheInfoEntity(
        mediaId = mediaId,
        torrentData = byteArrayOf(torrentData),
        relativeDir = relativeDir,
    )

    @Test
    fun `同一个 media 只有一行, 再次 upsert 覆盖而不是新增`() = runDatabaseTest { dao ->
        dao.upsert(entity(MEDIA_ID, 1, "/anitorrent/dir"))
        dao.upsert(entity(MEDIA_ID, 2, "/pikpak/dir"))

        assertEquals(1, dao.getAll().first().size)

        val row = assertNotNull(dao.get(MEDIA_ID))
        assertContentEquals(byteArrayOf(2), row.torrentData)
        assertEquals("/pikpak/dir", row.relativeDir)
    }

    @Test
    fun `删除只影响指定的 media`() = runDatabaseTest { dao ->
        dao.upsert(entity(MEDIA_ID, 1, "/anitorrent/dir"))
        dao.upsert(entity(OTHER_MEDIA_ID, 2, "/anitorrent/other"))

        dao.deleteByMediaId(MEDIA_ID)

        assertNull(dao.get(MEDIA_ID))
        assertNotNull(dao.get(OTHER_MEDIA_ID))
    }

    @Test
    fun `batchGet 只返回请求的 media`() = runDatabaseTest { dao ->
        dao.upsert(entity(MEDIA_ID, 1, "/anitorrent/dir"))
        dao.upsert(entity(OTHER_MEDIA_ID, 2, "/anitorrent/other"))

        val rows = dao.batchGet(listOf(MEDIA_ID))
        assertEquals(1, rows.size)
        assertEquals("/anitorrent/dir", rows.single().relativeDir)
    }

    private companion object {
        const val MEDIA_ID = "dmhy.1"
        const val OTHER_MEDIA_ID = "dmhy.2"
    }
}
