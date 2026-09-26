/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.playback

import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackHistorySyncStatusUiItemsTest {
    private val histories = mapOf(
        11 to EpisodeHistory(
            episodeId = 11,
            positionMillis = 0,
            subjectName = "葬送的芙莉莲",
            episodeName = "别离",
            deletedAtMillis = 200,
        ),
    )

    @Test
    fun `delete op takes names from deleted local record`() {
        val items = listOf(
            PlaybackHistoryPendingOp.Delete(id = 1, episodeId = 11, deletedAtMillis = 200),
        ).toSyncStatusUiItems(histories, upsertName = "更新", deleteName = "删除")

        val item = items.single()
        assertEquals("删除", item.operationName)
        assertEquals("葬送的芙莉莲", item.subjectName)
        assertEquals("别离", item.episodeName)
        assertEquals(200, item.versionMillis)
    }

    @Test
    fun `delete op without local record keeps names null`() {
        val item = listOf(
            PlaybackHistoryPendingOp.Delete(id = 1, episodeId = 99, deletedAtMillis = 200),
        ).toSyncStatusUiItems(histories, upsertName = "更新", deleteName = "删除").single()

        assertNull(item.subjectName)
        assertNull(item.episodeName)
    }

    @Test
    fun `upsert op prefers its own names and falls back to local record`() {
        val items = listOf(
            PlaybackHistoryPendingOp.Upsert(
                id = 1, episodeId = 11, subjectId = 1,
                subjectName = "自带名字", episodeName = null,
                positionMillis = 10, durationMillis = 100, updatedAtMillis = 300,
            ),
        ).toSyncStatusUiItems(histories, upsertName = "更新", deleteName = "删除")

        val item = items.single()
        assertEquals("更新", item.operationName)
        assertEquals("自带名字", item.subjectName)
        assertEquals("别离", item.episodeName)
    }
}
