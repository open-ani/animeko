/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 剧集看过状态的本地 outbox: 写本地、入队、被服务端刷新覆盖时保留.
 */
class EpisodeCollectionRepositoryPendingOpsTest {
    private object UnusedSubjectsApi : ApiInvoker<SubjectsAniApi> {
        override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R = error("ApiInvoker not expected")
    }

    private object UnusedScheduleApi : ApiInvoker<ScheduleAniApi> {
        override suspend fun <R> invoke(action: suspend ScheduleAniApi.() -> R): R = error("ApiInvoker not expected")
    }

    private class Fixture(val database: AniDatabase, val repository: EpisodeCollectionRepository, val dirtyCalls: () -> Int)

    private fun runRepositoryTest(now: Long = 1_000, block: suspend Fixture.() -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        var dirty = 0
        try {
            val repository = EpisodeCollectionRepository(
                subjectDao = database.subjectCollection(),
                episodeCollectionDao = database.episodeCollection(),
                pendingOpDao = database.episodeCollectionPendingOpDao(),
                episodeService = EpisodeServiceImpl(UnusedSubjectsApi),
                animeScheduleRepository = AnimeScheduleRepository(AnimeScheduleService(UnusedScheduleApi)),
                subjectCollectionRepository = lazy { error("SubjectCollectionRepository not expected") },
                getEpisodeTypeFiltersUseCase = GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) },
                nowMillis = { now },
                onDirtyChanged = { dirty++ },
            )
            Fixture(database, repository) { dirty }.block()
        } finally {
            database.close()
        }
    }

    private suspend fun Fixture.seed(subjectId: Int, episodeIds: List<Int>, type: UnifiedCollectionType = UnifiedCollectionType.WISH) {
        database.subjectCollection().upsert(listOf(subject(subjectId)))
        database.episodeCollection().upsert(episodeIds.map { episode(subjectId, it, type) })
    }

    private suspend fun Fixture.localType(episodeId: Int) =
        database.episodeCollection().findByEpisodeId(episodeId).first()!!.selfCollectionType

    @Test
    fun `setEpisodeCollectionType writes local and enqueues one op per episode`() = runRepositoryTest {
        seed(1, listOf(11, 12))

        repository.setEpisodeCollectionType(1, 11, UnifiedCollectionType.DONE)
        repository.setEpisodeCollectionType(1, 11, UnifiedCollectionType.WISH)
        repository.setEpisodeCollectionType(1, 12, UnifiedCollectionType.DONE)

        assertEquals(UnifiedCollectionType.WISH, localType(11))
        assertEquals(UnifiedCollectionType.DONE, localType(12))
        val ops = repository.pendingOpsFlow.first()
        assertEquals(listOf(11 to UnifiedCollectionType.WISH, 12 to UnifiedCollectionType.DONE), ops.map { it.episodeId to it.collectionType })
        assertTrue(ops.all { it.subjectId == 1 && it.updatedAtMillis == 1_000L })
        assertEquals(3, dirtyCalls())
    }

    @Test
    fun `remote refresh keeps locally pending collection type`() = runRepositoryTest {
        seed(1, listOf(11, 12))
        repository.setEpisodeCollectionType(1, 11, UnifiedCollectionType.DONE)

        // 服务端还不知道 11 已看过, 刷新时把两集都写成未看过
        database.episodeCollection().upsert(listOf(episode(1, 11, UnifiedCollectionType.WISH), episode(1, 12, UnifiedCollectionType.WISH)))

        assertEquals(UnifiedCollectionType.DONE, localType(11))
        assertEquals(UnifiedCollectionType.WISH, localType(12))

        // 同步完成后再刷新, 服务端的值才生效
        repository.deletePendingOps(repository.pendingOpsFlow.first().map { it.id })
        database.episodeCollection().upsert(episode(1, 11, UnifiedCollectionType.WISH))
        assertEquals(UnifiedCollectionType.WISH, localType(11))
        assertEquals(emptyList(), repository.pendingOpsFlow.first())
    }

    @Test
    fun `setAllEpisodesWatched enqueues every episode of the subject`() = runRepositoryTest {
        seed(1, listOf(11, 12, 13))
        seed(2, listOf(21))

        repository.setAllEpisodesWatched(1)

        assertEquals(setOf(11, 12, 13), repository.pendingOpsFlow.first().map { it.episodeId }.toSet())
        assertEquals(UnifiedCollectionType.DONE, localType(13))
        assertEquals(UnifiedCollectionType.WISH, localType(21))
    }

    @Test
    fun `pendingOpNamesFlow resolves names from local caches`() = runRepositoryTest {
        seed(1, listOf(11))
        repository.setEpisodeCollectionType(1, 11, UnifiedCollectionType.DONE)
        repository.setEpisodeCollectionType(9, 99, UnifiedCollectionType.DONE) // 本地没有这个剧集

        val names = repository.pendingOpNamesFlow().first()
        assertEquals(setOf(11), names.keys)
        assertEquals(EpisodeCollectionPendingOpNames(subjectName = "条目 1", episodeName = "第 11 集"), names.getValue(11))
    }

    private fun subject(subjectId: Int) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "subject-$subjectId",
        nameCn = "条目 $subjectId",
        summary = "",
        nsfw = false,
        imageLarge = "",
        totalEpisodes = 12,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo(score = 0, comment = null, tags = emptyList(), isPrivate = false),
        collectionType = UnifiedCollectionType.DOING,
        recurrence = null,
        lastUpdated = subjectId.toLong(),
        lastFetched = 0,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    private fun episode(subjectId: Int, episodeId: Int, type: UnifiedCollectionType) = EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId,
        episodeType = EpisodeType.MainStory,
        name = "ep",
        nameCn = "第 $episodeId 集",
        airDate = PackedDate.Invalid,
        comment = 0,
        desc = "",
        sort = EpisodeSort(episodeId),
        sortNumber = episodeId.toFloat(),
        selfCollectionType = type,
        lastFetched = 0,
    )
}
