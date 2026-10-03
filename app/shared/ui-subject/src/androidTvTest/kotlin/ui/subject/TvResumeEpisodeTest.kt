/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

class TvResumeEpisodeTest {
    private fun episode(id: Int, type: UnifiedCollectionType = UnifiedCollectionType.NOT_COLLECTED) =
        EpisodeListItem(id, EpisodeSort(id), null, "", "", type, true)

    @Test
    fun `resume target takes precedence over first unfinished episode`() {
        assertEquals(3, selectTvResumeEpisode(3, listOf(episode(1), episode(2), episode(3))))
    }

    @Test
    fun `invalid resume target falls back to unfinished episode`() {
        assertEquals(2, selectTvResumeEpisode(99, listOf(episode(1, UnifiedCollectionType.DONE), episode(2))))
    }

    @Test
    fun `completed and dropped episodes are skipped when selecting first unfinished`() {
        assertEquals(3, selectTvResumeEpisode(null, listOf(episode(1, UnifiedCollectionType.DONE), episode(2, UnifiedCollectionType.DROPPED), episode(3))))
    }

    @Test
    fun `completed series restarts from first episode and empty series cannot play`() {
        assertEquals(1, selectTvResumeEpisode(null, listOf(episode(1, UnifiedCollectionType.DONE))))
        assertNull(selectTvResumeEpisode(null, emptyList()))
        assertNull(selectTvResumeEpisode(1, emptyList()))
    }
}
