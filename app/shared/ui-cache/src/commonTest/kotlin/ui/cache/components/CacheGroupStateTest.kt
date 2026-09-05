/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import me.him188.ani.app.tools.toProgress
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class CacheGroupStateTest {
    private val finished = createTestCacheEpisode(
        1, episodeId = 1,
        initialState = CacheEpisodePaused.COMPLETED,
        progress = 1f.toProgress(),
        totalSize = 100.megaBytes,
        downloadSpeed = FileSize.Unspecified,
    )
    private val downloading = createTestCacheEpisode(
        2, episodeId = 2,
        initialState = CacheEpisodePaused.IN_PROGRESS,
        totalSize = 200.megaBytes,
        downloadSpeed = 10.megaBytes,
    )
    private val paused = createTestCacheEpisode(
        3, episodeId = 3,
        initialState = CacheEpisodePaused.PAUSED,
        totalSize = 300.megaBytes,
        downloadSpeed = 5.megaBytes,
    )

    private fun group(
        entries: List<CacheEpisodeState>,
        totalEpisodeCount: Int? = null,
    ) = CacheGroupState(
        subjectId = 1,
        subjectName = "孤独摇滚",
        entries = entries,
        collectionType = UnifiedCollectionType.DOING,
        totalEpisodeCount = totalEpisodeCount,
    )

    @Test
    fun `displayTotalCount uses totalEpisodeCount when known`() {
        assertEquals(12, group(listOf(finished, downloading), totalEpisodeCount = 12).displayTotalCount)
    }

    @Test
    fun `displayTotalCount falls back to entries size`() {
        assertEquals(2, group(listOf(finished, downloading)).displayTotalCount)
    }

    @Test
    fun `displayTotalCount is at least entries size`() {
        assertEquals(3, group(listOf(finished, downloading, paused), totalEpisodeCount = 1).displayTotalCount)
    }

    @Test
    fun `totalSize sums specified sizes`() {
        assertEquals(600.megaBytes, group(listOf(finished, downloading, paused)).totalSize)
    }

    @Test
    fun `totalSize is unspecified when no entry has size`() {
        val entry = createTestCacheEpisode(1, totalSize = FileSize.Unspecified)
        assertEquals(FileSize.Unspecified, group(listOf(entry)).totalSize)
    }

    @Test
    fun `downloadSpeed only counts actively downloading entries`() {
        // paused 的 5MB/s 不应计入
        assertEquals(10.megaBytes, group(listOf(finished, downloading, paused)).downloadSpeed)
    }

    @Test
    fun `downloadSpeedText is null when nothing is downloading`() {
        assertNull(group(listOf(finished, paused)).downloadSpeedText)
        assertEquals(FileSize.Unspecified, group(listOf(finished, paused)).downloadSpeed)
    }

    @Test
    fun `activeDownloadCount counts only in progress`() {
        assertEquals(1, group(listOf(finished, downloading, paused)).activeDownloadCount)
    }

    @Test
    fun `hasUnfinished reflects unfinished entries`() {
        assertTrue(group(listOf(finished, downloading)).hasUnfinished)
        assertFalse(group(listOf(finished)).hasUnfinished)
    }
}
