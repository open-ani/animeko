/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import me.him188.ani.app.ui.download.components.createTestDownloadItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class SubjectDownloadsPresentationTest {
    @Test
    fun `multiple downloads of one episode retain separate identities and are deduplicated`() {
        val first = createTestDownloadItem(1).copy(id = "first")
        val second = createTestDownloadItem(1).copy(id = "second")
        val items = buildSubjectDownloadItems(listOf(episode(1), episode(2)), listOf(first, second, first))
        assertEquals(3, items.size)
        assertEquals(3, items.map { it.key }.distinct().size)
        assertEquals(2, items.filterIsInstance<SubjectDownloadListItem.Download>().size)
        assertEquals(2, assertIs<SubjectDownloadListItem.Episode>(items.last()).episode.episodeId)
    }

    @Test
    fun `downloads remain visible without episode metadata`() {
        val download = createTestDownloadItem(2)
        assertEquals(listOf(SubjectDownloadListItem.Download(download)), buildSubjectDownloadItems(emptyList(), listOf(download)))
    }

    @Test
    fun `new episodes and changed watch status are reflected without changing download identity`() {
        val download = createTestDownloadItem(1)
        val initial = buildSubjectDownloadItems(listOf(episode(1)), listOf(download))
        val updated = buildSubjectDownloadItems(listOf(episode(1), episode(2).copy(watchStatus = UnifiedCollectionType.DONE)), listOf(download))
        assertEquals(initial.first(), updated.first())
        assertEquals(UnifiedCollectionType.DONE, assertIs<SubjectDownloadListItem.Episode>(updated.last()).episode.watchStatus)
    }

    private fun episode(id: Int) = EpisodeDownloadItem(id, EpisodeSort(id), "Episode $id", UnifiedCollectionType.DOING, true)
}
