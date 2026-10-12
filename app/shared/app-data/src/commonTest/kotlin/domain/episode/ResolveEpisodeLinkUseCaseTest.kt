/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResolveEpisodeLinkUseCaseTest {
    private val main = EpisodeInfo(101, EpisodeType.MainStory, sort = EpisodeSort(13), ep = EpisodeSort(1))
    private val special = EpisodeInfo(102, EpisodeType.SP, sort = EpisodeSort(1), ep = EpisodeSort(1))

    private fun resolver(episodes: List<EpisodeInfo>) = ResolveEpisodeLinkUseCase(
        getEpisodes = { assertEquals(12, it); episodes },
        getSubjectId = { error("Unexpected episode lookup") },
    )

    @Test
    fun `match bangumi ep instead of sort or special episode`() = runTest {
        val resolve = resolver(listOf(special, main))
        assertEquals(EpisodeLinkDestination(12, 101), resolve(EpisodeLink.Subject(12, EpisodeSort(1))))
        assertEquals(EpisodeLinkDestination(12), resolve(EpisodeLink.Subject(12, EpisodeSort(13))))
    }

    @Test
    fun `missing or ambiguous ep falls back to subject`() = runTest {
        val cases = listOf(
            emptyList(), listOf(special), listOf(main.copy(ep = null)), listOf(main, main.copy(episodeId = 103)),
        )
        for (episodes in cases) {
            assertEquals(EpisodeLinkDestination(12), resolver(episodes)(EpisodeLink.Subject(12, EpisodeSort(1))))
        }
    }

    @Test
    fun `fractional ep is matched`() = runTest {
        val resolve = resolver(listOf(main.copy(ep = EpisodeSort("1.5"))))
        assertEquals(EpisodeLinkDestination(12, 101), resolve(EpisodeLink.Subject(12, EpisodeSort("1.5"))))
    }

    @Test
    fun `subject without ep needs no lookup`() = runTest {
        val resolve = ResolveEpisodeLinkUseCase({ error("Unexpected lookup") }, { error("Unexpected lookup") })
        assertEquals(EpisodeLinkDestination(12), resolve(EpisodeLink.Subject(12)))
    }

    @Test
    fun `episode id resolves its subject`() = runTest {
        val resolve = ResolveEpisodeLinkUseCase(
            getEpisodes = { error("Unexpected subject lookup") },
            getSubjectId = { assertEquals(102, it); 12 },
        )
        assertEquals(EpisodeLinkDestination(12, 102), resolve(EpisodeLink.Episode(102)))
    }

    @Test
    fun `unknown episode does not navigate`() = runTest {
        assertNull(ResolveEpisodeLinkUseCase({ emptyList() }, { null })(EpisodeLink.Episode(102)))
    }

    @Test
    fun `lookup errors fall back safely`() = runTest {
        val resolve = ResolveEpisodeLinkUseCase({ error("Offline") }, { error("Offline") })
        assertEquals(EpisodeLinkDestination(12), resolve(EpisodeLink.Subject(12, EpisodeSort(1))))
        assertNull(resolve(EpisodeLink.Episode(102)))
    }

    @Test
    fun `cancelled lookup does not navigate`() = runTest {
        val resolve = ResolveEpisodeLinkUseCase({ throw CancellationException() }, { throw CancellationException() })
        assertFailsWith<CancellationException> { resolve(EpisodeLink.Subject(12, EpisodeSort(1))) }
        assertFailsWith<CancellationException> { resolve(EpisodeLink.Episode(102)) }
    }
}
