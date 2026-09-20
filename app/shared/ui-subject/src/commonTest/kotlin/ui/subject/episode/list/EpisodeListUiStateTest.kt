/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.models.subject.createTestSubjectCollection
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * 剧集列表 "未开播" 着色规则: [EpisodeListUiState.isEpisodeBroadcast], 以及 [EpisodeListUiState.from] 对它的使用.
 */
@OptIn(TestOnly::class)
class EpisodeListUiStateTest {
    /** 周五 14:30Z 每周 (545917 これ描いて死ね) */
    private val recurrence = SubjectRecurrence(Instant.parse("2026-07-03T14:30:00Z"), 7.days)
    private val now = Instant.parse("2026-09-04T00:00:00Z")

    private fun episode(sort: Int, airDate: PackedDate) = EpisodeCollectionInfo(
        episodeInfo = EpisodeInfo(
            episodeId = sort,
            type = EpisodeType.MainStory,
            sort = EpisodeSort(sort),
            airDate = airDate,
        ),
        collectionType = UnifiedCollectionType.WISH,
    )

    private fun uiState(
        recurrence: SubjectRecurrence?,
        currentTime: Instant,
        vararg episodes: EpisodeCollectionInfo,
    ): EpisodeListUiState {
        val collection = createTestSubjectCollection(1, episodes.toList(), UnifiedCollectionType.DOING)
            .copy(recurrence = recurrence)
        return EpisodeListUiState.from(collection, currentTime)
    }

    @Test
    fun `from_copies_still_urls_onto_list_items`() {
        val episode = EpisodeCollectionInfo(
            episodeInfo = EpisodeInfo(
                episodeId = 1,
                type = EpisodeType.MainStory,
                sort = EpisodeSort(1),
                imageMedium = "https://static.example/tmdb/w300/a.jpg",
                imageLarge = "https://static.example/tmdb/original/a.jpg",
            ),
            collectionType = UnifiedCollectionType.WISH,
        )

        val item = uiState(null, now, episode).mainEpisodes.single()

        assertEquals("https://static.example/tmdb/w300/a.jpg", item.imageMedium)
        assertEquals("https://static.example/tmdb/original/a.jpg", item.imageLarge)
        assertEquals(null, uiState(null, now, episode.copy(episodeInfo = episode.episodeInfo.copy(imageMedium = null))).mainEpisodes.single().imageMedium)
    }

    @Test
    fun `from_attaches_play_progress_by_episode_id`() {
        val collection = createTestSubjectCollection(
            1,
            listOf(episode(1, PackedDate.Invalid), episode(2, PackedDate.Invalid)),
            UnifiedCollectionType.DOING,
        )
        val state = EpisodeListUiState.from(collection, now, playProgress = mapOf(2 to 0.4f))
        assertEquals(null, state.mainEpisodes[0].playProgress)
        assertEquals(0.4f, state.mainEpisodes[1].playProgress)
        assertEquals(null, EpisodeListUiState.from(collection, now).mainEpisodes[1].playProgress)
    }

    @Test
    fun `blank_air_date_with_recurrence_is_not_broadcast`() {
        assertFalse(EpisodeListUiState.isEpisodeBroadcast(recurrence, PackedDate.Invalid, now))
        assertFalse(uiState(recurrence, now, episode(1, PackedDate.Invalid)).mainEpisodes.single().isBroadcast)
    }

    @Test
    fun `blank_air_date_without_recurrence_is_broadcast`() {
        assertTrue(EpisodeListUiState.isEpisodeBroadcast(null, PackedDate.Invalid, now))
        assertTrue(uiState(null, now, episode(1, PackedDate.Invalid)).mainEpisodes.single().isBroadcast)
    }

    @Test
    fun `dated_past_episode_is_broadcast_with_and_without_recurrence`() {
        val past = PackedDate(2020, 1, 1)
        assertTrue(EpisodeListUiState.isEpisodeBroadcast(recurrence, past, now))
        assertTrue(EpisodeListUiState.isEpisodeBroadcast(null, past, now))
        assertTrue(uiState(recurrence, now, episode(1, past)).mainEpisodes.single().isBroadcast)
        assertTrue(uiState(null, now, episode(1, past)).mainEpisodes.single().isBroadcast)
    }

    @Test
    fun `dated_future_episode_is_not_broadcast_with_and_without_recurrence`() {
        val future = PackedDate(8888, 1, 1)
        assertFalse(EpisodeListUiState.isEpisodeBroadcast(recurrence, future, now))
        assertFalse(EpisodeListUiState.isEpisodeBroadcast(null, future, now))
        assertFalse(uiState(recurrence, now, episode(1, future)).mainEpisodes.single().isBroadcast)
        assertFalse(uiState(null, now, episode(1, future)).mainEpisodes.single().isBroadcast)
    }

    @Test
    fun `exact_slot_flips_at_the_slot_instant`() {
        val airDate = PackedDate(2026, 7, 10) // ep2 -> 2026-07-10T14:30Z
        val slot = Instant.parse("2026-07-10T14:30:00Z")
        assertFalse(EpisodeListUiState.isEpisodeBroadcast(recurrence, airDate, slot - 1.milliseconds))
        assertTrue(EpisodeListUiState.isEpisodeBroadcast(recurrence, airDate, slot))
        assertTrue(EpisodeListUiState.isEpisodeBroadcast(recurrence, airDate, slot + 1.milliseconds))
    }

    @Test
    fun `from_sets_subjectTitle_and_subjectOriginalTitle_from_the_subjects_name_and_nameCn`() {
        val collection = createTestSubjectCollection(1, listOf(episode(1, PackedDate.Invalid)), UnifiedCollectionType.DOING)
            .let { it.copy(subjectInfo = it.subjectInfo.copy(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")) }
        val state = EpisodeListUiState.from(collection, now)
        assertEquals("孤独摇滚！", state.subjectTitle)
        assertEquals("ぼっち・ざ・ろっく！", state.subjectOriginalTitle)
    }

    @Test
    fun `from_applies_the_rule_per_episode_and_sorts_by_sort`() {
        val state = uiState(
            recurrence, now,
            episode(3, PackedDate.Invalid),
            episode(1, PackedDate(2026, 7, 3)),
            episode(2, PackedDate(2026, 7, 10)),
        )
        assertEquals(listOf(1, 2, 3), state.mainEpisodes.map { it.episodeId })
        assertEquals(listOf(true, true, false), state.mainEpisodes.map { it.isBroadcast })
        assertTrue(state.otherEpisodes.isEmpty())
    }
}
