/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.source.MediaFetchRequest

class MediaFetchRequestEpisodeNumbersTest {
    private val episode = EpisodeInfo(episodeId = 10, type = EpisodeType.MainStory, sort = EpisodeSort(1), ep = EpisodeSort(1))
    private val other = EpisodeInfo(episodeId = 11, type = EpisodeType.MainStory, sort = EpisodeSort(2), ep = EpisodeSort(2))

    private fun request(episodeId: Int, sort: Int) = MediaFetchRequest(
        subjectId = "1",
        episodeId = episodeId.toString(),
        subjectNames = listOf("A"),
        episodeSort = EpisodeSort(sort),
        episodeName = "",
        episodeEp = EpisodeSort(sort),
    )

    @Test
    fun `requested numbers apply only to the episode the request points at`() {
        val edited = episode.withRequestedNumbers(request(10, sort = 5))
        assertEquals(EpisodeSort(5), edited.sort)
        assertEquals(EpisodeSort(5), edited.ep)
        assertSame(other, other.withRequestedNumbers(request(10, sort = 5)))
        assertSame(episode, episode.withRequestedNumbers(request(10, sort = 1)))
    }
}
