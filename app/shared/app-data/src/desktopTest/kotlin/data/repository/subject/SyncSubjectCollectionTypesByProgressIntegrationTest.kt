/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MyCollectionsSettings
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.TestSubjectAiringInfos
import me.him188.ani.app.data.models.subject.createTestSubjectCollection
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionSyncer
import me.him188.ani.app.data.repository.episode.toEntity
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCaseImpl
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 使用真实 Room outbox 和剧集同步器验证自动收藏后的剧集状态重试. */
class SyncSubjectCollectionTypesByProgressIntegrationTest {
    private class LocalSubjects(private val database: AniDatabase) : SubjectCollectionRepository() {
        var reads = 0

        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = flow {
            reads++
            val type = database.subjectCollection().findById(subjectId).first()!!.collectionType
            val episodes = database.episodeCollection().filterBySubjectId(subjectId).first()
                .map { it.toEpisodeCollectionInfo() }
            emit(createTestSubjectCollection(subjectId, episodes, type).copy(airingInfo = TestSubjectAiringInfos.OnAir12Eps))
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = unused()
        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> = unused()
        override fun cachedValidSubjectIds(): Flow<List<Int>> = unused()
        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ): Unit = unused()
        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = unused()
        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ): Unit = unused()
        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?): Unit = unused()
        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> = unused()
        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> = unused()
        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            unused()
        override suspend fun performBangumiFullSync(): Unit = unused()
        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = unused()
        override suspend fun invalidateCache(subjectIds: List<Int>): Unit = unused()
        override suspend fun invalidateAllCaches(): Unit = unused()
        private fun unused(): Nothing = error("Unexpected subject repository operation")
    }

    private object UnusedScheduleApi : ApiInvoker<ScheduleAniApi> {
        override suspend fun <R> invoke(action: suspend ScheduleAniApi.() -> R): R = error("Unexpected schedule request")
    }

    private object ValidSession : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = flowOf(SessionState.Valid(bangumiConnected = true))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private class Fixture(val database: AniDatabase, private val scope: CoroutineScope) {
        private val progressScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job))
        val enabled = MutableStateFlow(MyCollectionsSettings.Default.autoAdvanceCollectionType)
        val subjects = LocalSubjects(database)
        val collectionCalls = mutableListOf<Pair<Int, UnifiedCollectionType>>()
        var beforeCollect: suspend () -> Unit = {}
        var serverCollected = false
        var episodeRequests = 0
        var dirtyCalls = 0

        private val api = SubjectsAniApi(
            baseUrl = "http://test",
            httpClientEngine = MockEngine {
                episodeRequests++
                respond("{}", if (serverCollected) HttpStatusCode.OK else HttpStatusCode.NotFound)
            },
            httpClientConfig = { it.expectSuccess = true },
        )
        private val apiInvoker = object : ApiInvoker<SubjectsAniApi> {
            override suspend fun <R> invoke(action: suspend SubjectsAniApi.() -> R): R = api.action()
        }
        val episodes = EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            pendingOpDao = database.episodeCollectionPendingOpDao(),
            episodeService = EpisodeServiceImpl(apiInvoker),
            animeScheduleRepository = AnimeScheduleRepository(AnimeScheduleService(UnusedScheduleApi)),
            subjectCollectionRepository = lazy { subjects },
            getEpisodeTypeFiltersUseCase = GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) },
            onDirtyChanged = { dirtyCalls++ },
        )
        val syncer = EpisodeCollectionSyncer(episodes, apiInvoker, ValidSession, scope)
        val progress = SyncSubjectCollectionTypesByProgressUseCaseImpl(
            subjects,
            object : SetSubjectCollectionTypeOrDeleteUseCase {
                override suspend fun invoke(subjectId: Int, collectionType: UnifiedCollectionType?) {
                    val targetType = requireNotNull(collectionType)
                    collectionCalls += subjectId to targetType
                    beforeCollect()
                    serverCollected = true
                    database.subjectCollection().updateType(subjectId, targetType)
                }
            },
            episodes,
            enabled,
            progressScope,
        )

        suspend fun seed(subjectId: Int = 1, type: UnifiedCollectionType = UnifiedCollectionType.NOT_COLLECTED) {
            database.subjectCollection().upsert(
                SubjectCollectionEntity(
                    subjectId = subjectId,
                    name = "subject-$subjectId",
                    nameCn = "",
                    summary = "",
                    nsfw = false,
                    imageLarge = "",
                    totalEpisodes = 2,
                    airDate = PackedDate.Invalid,
                    aliases = emptyList(),
                    tags = emptyList(),
                    collectionStats = SubjectCollectionStats.Zero,
                    ratingInfo = RatingInfo.Empty,
                    completeDate = PackedDate.Invalid,
                    selfRatingInfo = SelfRatingInfo(0, null, emptyList(), false),
                    collectionType = type,
                    recurrence = null,
                    lastUpdated = 0,
                    lastFetched = 0,
                    cachedStaffUpdated = 0,
                    cachedCharactersUpdated = 0,
                ),
            )
            database.episodeCollection().upsert(
                (1..2).map { sort ->
                    EpisodeCollectionInfo(
                        EpisodeInfo(
                            episodeId = subjectId * 10 + sort,
                            type = EpisodeType.MainStory,
                            sort = EpisodeSort(sort),
                        ),
                        UnifiedCollectionType.NOT_COLLECTED,
                    ).toEntity(subjectId)
                },
            )
        }

        suspend fun advance() {
            progress.requestSubjectSync(1)
            awaitSync()
        }

        suspend fun awaitSync() {
            progressScope.coroutineContext.job.children.toList().joinAll()
        }

        suspend fun localType(episodeId: Int) =
            database.episodeCollection().findByEpisodeId(episodeId).first()!!.selfCollectionType
    }

    private fun runProgressTest(block: suspend Fixture.() -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        try {
            Fixture(database, scope).block()
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `default disabled skips subject reads and live setting enables automatic collection`() = runProgressTest {
        seed()
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.DONE)

        assertFalse(enabled.value)
        advance()
        assertEquals(0, subjects.reads)
        assertTrue(collectionCalls.isEmpty())
        assertEquals(1, dirtyCalls)

        enabled.value = true
        advance()
        assertEquals(listOf(1 to UnifiedCollectionType.DOING), collectionCalls)
        assertEquals(2, dirtyCalls)
    }

    @Test
    fun `episode use case only requests automatic collection for done`() = runProgressTest {
        seed()
        enabled.value = true
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        val application = koinApplication {
            modules(module {
                single { episodes }
                single<SyncSubjectCollectionTypesByProgressUseCase> { progress }
            })
        }
        try {
            val setEpisode = SetEpisodeCollectionTypeUseCaseImpl(application.koin)
            setEpisode(1, episodeId, UnifiedCollectionType.WISH)
            awaitSync()
            assertEquals(0, subjects.reads)
            assertTrue(collectionCalls.isEmpty())

            setEpisode(1, episodeId, UnifiedCollectionType.DONE)
            awaitSync()
            assertEquals(listOf(1 to UnifiedCollectionType.DOING), collectionCalls)
        } finally {
            application.close()
        }
    }

    @Test
    fun `episode rejected before collection is requeued and syncs after collection`() = runProgressTest {
        seed()
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.DONE)
        beforeCollect = {
            syncer.syncOnce()
            assertTrue(episodes.pendingOpsFlow.first().isEmpty())
            assertEquals(UnifiedCollectionType.DONE, localType(episodeId))
        }
        enabled.value = true

        advance()

        assertEquals(listOf(episodeId), episodes.pendingOpsFlow.first().map { it.episodeId })
        syncer.syncOnce()
        assertTrue(episodes.pendingOpsFlow.first().isEmpty())
        assertEquals(2, episodeRequests)
        assertEquals(listOf(1 to UnifiedCollectionType.DOING), collectionCalls)
    }

    @Test
    fun `requeued ops survive deletion by an older in flight sync and exclude other subjects`() = runProgressTest {
        seed()
        seed(2)
        val subjectEpisodes = database.episodeCollection().filterBySubjectId(1).first()
        for (episode in subjectEpisodes) {
            episodes.setEpisodeCollectionType(1, episode.episodeId, UnifiedCollectionType.DONE)
        }
        val otherEpisodeId = database.episodeCollection().filterBySubjectId(2).first().first().episodeId
        episodes.setEpisodeCollectionType(2, otherEpisodeId, UnifiedCollectionType.DONE)
        val oldOps = episodes.pendingOpsFlow.first()
        enabled.value = true

        advance()
        episodes.deletePendingOps(oldOps.map { it.id })

        val pending = episodes.pendingOpsFlow.first()
        assertEquals(subjectEpisodes.map { it.episodeId }.toSet(), pending.map { it.episodeId }.toSet())
        assertTrue(pending.all { it.id > oldOps.maxOf { op -> op.id } })
    }

    @Test
    fun `unwatching while collection is being created keeps the latest episode operation`() = runProgressTest {
        seed()
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.DONE)
        beforeCollect = { episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.WISH) }
        enabled.value = true

        advance()

        assertEquals(UnifiedCollectionType.WISH, localType(episodeId))
        assertEquals(listOf(UnifiedCollectionType.WISH), episodes.pendingOpsFlow.first().map { it.collectionType })
    }

    @Test
    fun `failed collection leaves the local done state and original pending op intact`() = runProgressTest {
        seed()
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.DONE)
        val pending = episodes.pendingOpsFlow.first()
        beforeCollect = { throw IOException("offline") }
        enabled.value = true

        advance()

        assertFalse(serverCollected)
        assertEquals(UnifiedCollectionType.DONE, localType(episodeId))
        assertEquals(pending, episodes.pendingOpsFlow.first())
        assertEquals(1, dirtyCalls)
    }

    @Test
    fun `existing wish collection advances without requeueing episode operations`() = runProgressTest {
        seed(type = UnifiedCollectionType.WISH)
        val episodeId = database.episodeCollection().filterBySubjectId(1).first().first().episodeId
        episodes.setEpisodeCollectionType(1, episodeId, UnifiedCollectionType.DONE)
        val pending = episodes.pendingOpsFlow.first()
        enabled.value = true

        advance()

        assertEquals(listOf(1 to UnifiedCollectionType.DOING), collectionCalls)
        assertEquals(pending, episodes.pendingOpsFlow.first())
        assertEquals(1, dirtyCalls)
    }
}
