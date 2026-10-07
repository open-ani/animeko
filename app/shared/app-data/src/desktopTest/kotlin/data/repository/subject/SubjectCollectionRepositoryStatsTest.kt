/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BatchSubjectRelations
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.client.models.AniSubjectStats
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 修改收藏类型或评分后, 服务端返回的全站统计与修改一起写入本地缓存.
 */
class SubjectCollectionRepositoryStatsTest {
    private class FakeSubjectService : SubjectService {
        val patches = mutableListOf<Pair<Int, AniUpdateSubjectCollectionRequest>>()
        var stats = AniSubjectStats(
            favorite = AniFavourite(wish = 0, done = 0, doing = 0, onHold = 0, dropped = 0),
            scoreDetails = emptyMap(),
        )

        override suspend fun patchSubjectCollection(
            subjectId: Int,
            payload: AniUpdateSubjectCollectionRequest,
        ): AniSubjectStats {
            patches += subjectId to payload
            return stats
        }

        override suspend fun getSubjectCollection(subjectId: Int): AniSubjectCollection? =
            throw UnsupportedOperationException()

        override suspend fun getSubjectCollections(
            type: BangumiSubjectCollectionType?,
            offset: Int,
            limit: Int,
        ): List<AniSubjectCollection> = throw UnsupportedOperationException()

        override suspend fun getSubjectRelations(
            subjectId: Int,
            withCharacterActors: Boolean,
        ): BatchSubjectRelations = throw UnsupportedOperationException()

        override fun subjectCollectionById(subjectId: Int): Flow<AniSubjectCollection?> =
            throw UnsupportedOperationException()

        override suspend fun deleteSubjectCollection(subjectId: Int) = throw UnsupportedOperationException()

        override suspend fun getSubjectRecommendations(subjectId: Int, limit: Int): List<AniSubjectRecommendation> =
            throw UnsupportedOperationException()

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts> =
            throw UnsupportedOperationException()

        override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
    }

    private object UnusedSubjectsApi : ApiInvoker<SubjectsAniApi> {
        override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R {
            error("ApiInvoker not expected in tests")
        }
    }

    private object UnusedScheduleApi : ApiInvoker<ScheduleAniApi> {
        override suspend fun <R> invoke(action: suspend ScheduleAniApi.() -> R): R {
            error("ApiInvoker not expected in tests")
        }
    }

    private class FakeSessionStateProvider : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = MutableStateFlow(SessionState.Valid(bangumiConnected = true))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private class Fixture(
        val database: AniDatabase,
        val service: FakeSubjectService,
        val repository: SubjectCollectionRepository,
    )

    private fun runRepositoryTest(block: suspend Fixture.() -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        try {
            val service = FakeSubjectService()
            val episodeService = EpisodeServiceImpl(UnusedSubjectsApi)
            val animeScheduleRepository = AnimeScheduleRepository(AnimeScheduleService(UnusedScheduleApi))
            val getEpisodeTypeFiltersUseCase = GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) }
            lateinit var repository: SubjectCollectionRepositoryImpl
            val episodeCollectionRepository = EpisodeCollectionRepository(
                subjectDao = database.subjectCollection(),
                episodeCollectionDao = database.episodeCollection(),
                pendingOpDao = database.episodeCollectionPendingOpDao(),
                episodeService = episodeService,
                animeScheduleRepository = animeScheduleRepository,
                subjectCollectionRepository = lazy { repository },
                getEpisodeTypeFiltersUseCase = getEpisodeTypeFiltersUseCase,
            )
            repository = SubjectCollectionRepositoryImpl(
                subjectService = service,
                subjectCollectionDao = database.subjectCollection(),
                subjectRelationsDao = database.subjectRelations(),
                episodeCollectionRepository = episodeCollectionRepository,
                animeScheduleRepository = animeScheduleRepository,
                episodeService = episodeService,
                episodeCollectionDao = database.episodeCollection(),
                sessionManager = FakeSessionStateProvider(),
                nsfwModeSettingsFlow = flowOf(NsfwMode.DISPLAY),
                getEpisodeTypeFiltersUseCase = getEpisodeTypeFiltersUseCase,
            )
            Fixture(database, service, repository).block()
        } finally {
            database.close()
        }
    }

    private fun cachedSubject(subjectId: Int) = SubjectCollectionEntity(
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
        collectionStats = SubjectCollectionStats(wish = 5, doing = 3, done = 10, onHold = 1, dropped = 0),
        ratingInfo = RatingInfo(rank = 100, total = 4, count = RatingCounts(s8 = 4), score = "8.0"),
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo.Empty,
        collectionType = UnifiedCollectionType.WISH,
        recurrence = null,
        lastUpdated = 0,
        // 未过期, subjectCollectionFlow 不会向服务端重新拉取
        lastFetched = currentTimeMillis(),
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    @Test
    fun `setting the collection type stores the returned stats together with the type`() = runRepositoryTest {
        database.subjectCollection().upsert(cachedSubject(1))
        service.stats = AniSubjectStats(
            favorite = AniFavourite(wish = 4, done = 11, doing = 3, onHold = 1, dropped = 0),
            scoreDetails = mapOf("8" to 4),
            score = "8.0",
            rank = 100,
        )

        repository.subjectCollectionFlow(1)
            .map { it.collectionType to it.subjectInfo.collectionStats }
            .distinctUntilChanged()
            .test {
            assertEquals(UnifiedCollectionType.WISH to SubjectCollectionStats(5, 3, 10, 1, 0), awaitItem())

            repository.setSubjectCollectionTypeOrDelete(1, UnifiedCollectionType.DONE)

            // 类型与统计在同一个事务里写入, 不会先看到新类型、旧统计
            assertEquals(UnifiedCollectionType.DONE to SubjectCollectionStats(4, 3, 11, 1, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(AniCollectionType.DONE, service.patches.single().second.collectionType)
    }

    @Test
    fun `rating stores the returned rating distribution and score`() = runRepositoryTest {
        database.subjectCollection().upsert(cachedSubject(2))
        service.stats = AniSubjectStats(
            favorite = AniFavourite(wish = 5, done = 10, doing = 3, onHold = 1, dropped = 0),
            scoreDetails = mapOf("8" to 4, "10" to 1),
            score = "8.4",
            rank = 100,
        )

        repository.updateRating(2, score = 10, comment = null, tags = null, isPrivate = null)

        val info = repository.subjectCollectionFlow(2).first()
        assertEquals(10, info.selfRatingInfo.score)
        assertEquals(
            RatingInfo(rank = 100, total = 5, count = RatingCounts(s8 = 4, s10 = 1), score = "8.4"),
            info.subjectInfo.ratingInfo,
        )
        assertEquals(SubjectCollectionStats(5, 3, 10, 1, 0), info.subjectInfo.collectionStats)
    }
}
