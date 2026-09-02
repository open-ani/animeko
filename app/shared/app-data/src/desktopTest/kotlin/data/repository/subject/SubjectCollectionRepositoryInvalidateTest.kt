/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BatchSubjectRelations
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniSubjectType
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.currentTimeMillis
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * 覆盖 [SubjectCollectionRepository.invalidateCache] 与 [SubjectCollectionRepository.invalidateAllCaches],
 * 使用真实的内存 Room 库与 [SubjectCollectionRepositoryImpl].
 *
 * 覆盖: 服务端没有该条目时删除 / 保留其他条目并重置 lastFetched / 剧集级联删除 / 空列表与未知 id / 全部失效 /
 * 失效后条目页重新拉取 / 服务端仍有收藏时重新拉取覆盖 / 未收藏时删除 / 网络失败保留 / 已在展示的分页器跟着更新 / 失效事件 /
 * 重新拉取的并行度上限与首次失败后不再发起新的拉取.
 */
class SubjectCollectionRepositoryInvalidateTest {

    private class FakeSubjectService : SubjectService {
        /**
         * 服务端上的条目 (含用户收藏状态), 按 id. [getSubjectCollection] 与 [getSubjectCollections] 都从这里取;
         * 不在其中的 id 视为条目不存在 (返回 `null`).
         */
        val serverSubjects = mutableMapOf<Int, AniSubjectCollection>()

        /**
         * [getSubjectCollection] 对这些 id 抛出异常 (模拟网络失败). 若设置了 [gate], 在放行之后才抛出.
         */
        val failingSubjectIds = mutableSetOf<Int>()

        /**
         * [getSubjectCollection] 被调用的 subjectId, 按调用顺序 (并行拉取时顺序不确定).
         */
        val fetchedSubjectIds = CopyOnWriteArrayList<Int>()
        val firstFetch = CompletableDeferred<Int>()

        /**
         * 非空时每次 [getSubjectCollection] 记录调用后挂起, 直到放行. 用于观察并行度.
         */
        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        /**
         * 非空时**第一个** [getSubjectCollection] 调用 (无论 id) 等待它放行后抛出异常, 不受 [gate] 影响.
         * 用于确定性地制造 "已有请求在途时发生首次失败".
         */
        @Volatile
        var failFirstFetchGate: CompletableDeferred<Unit>? = null

        /**
         * 因 [failFirstFetchGate] 而失败的 subjectId.
         */
        @Volatile
        var failedSubjectId: Int? = null

        /**
         * 已开始的调用数 / 当前在途调用数 / 在途调用数的峰值.
         */
        val started = AtomicInteger(0)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        override suspend fun getSubjectCollection(subjectId: Int): AniSubjectCollection? {
            fetchedSubjectIds += subjectId
            val index = started.incrementAndGet()
            firstFetch.complete(subjectId)
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            try {
                val failGate = failFirstFetchGate
                if (index == 1 && failGate != null) {
                    failedSubjectId = subjectId
                    failGate.await()
                    throw IllegalStateException("network down")
                }
                gate?.await()
                if (subjectId in failingSubjectIds) throw IllegalStateException("network down")
                return serverSubjects[subjectId]
            } finally {
                inFlight.decrementAndGet()
            }
        }

        /**
         * 分页器的 RemoteMediator 用: 返回服务端已收藏的条目 (不按类型过滤, 测试里只放同一类型). 超出范围返回空 (分页结束).
         */
        override suspend fun getSubjectCollections(
            type: BangumiSubjectCollectionType?,
            offset: Int,
            limit: Int,
        ): List<AniSubjectCollection> = serverSubjects.values
            .filter { it.collectionType != null }
            .sortedByDescending { it.id }
            .drop(offset)
            .take(limit)

        override suspend fun getSubjectRelations(
            subjectId: Int,
            withCharacterActors: Boolean,
        ): BatchSubjectRelations = throw UnsupportedOperationException()

        override fun subjectCollectionById(subjectId: Int): Flow<AniSubjectCollection?> =
            throw UnsupportedOperationException()

        override suspend fun patchSubjectCollection(subjectId: Int, payload: AniUpdateSubjectCollectionRequest) =
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
    ) {
        val dao: SubjectCollectionDao get() = database.subjectCollection()
    }

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

    private fun subject(
        subjectId: Int,
        lastFetched: Long,
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 0,
    ) = SubjectCollectionEntity(
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
        selfRatingInfo = SelfRatingInfo(score = score, comment = null, tags = emptyList(), isPrivate = false),
        collectionType = type,
        recurrence = null,
        lastUpdated = subjectId.toLong(),
        lastFetched = lastFetched,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    /**
     * 服务端返回的条目. [type] 为 `null` 表示条目存在但用户未收藏.
     */
    private fun serverSubject(
        subjectId: Int,
        type: AniCollectionType? = AniCollectionType.DOING,
        score: Int = 0,
        episodeIds: List<Int> = emptyList(),
    ) = AniSubjectCollection(
        id = subjectId.toLong(),
        type = AniSubjectType.ANIME,
        name = "subject-$subjectId",
        nameCn = "条目 $subjectId (服务端)",
        summary = "",
        nsfw = false,
        airDate = "2024-01-01",
        aliases = emptyList(),
        favorite = AniFavourite(wish = 0, done = 0, doing = 0, onHold = 0, dropped = 0),
        tags = emptyList(),
        metaTags = emptyList(),
        scoreDetails = emptyMap(),
        selfRating = AniSelfRatingInfo(score = score, tags = emptyList(), isPrivate = false, comment = null),
        episodes = episodeIds.mapIndexed { index, episodeId ->
            AniEpisodeCollection(
                episodeId = episodeId.toLong(),
                subjectId = subjectId.toLong(),
                sort = (index + 1).toString(),
                type = AniEpisodeType.MAIN,
                name = "ep",
                nameCn = "第 ${index + 1} 集",
                description = "",
                collectionType = AniEpisodeCollectionType.DONE,
            )
        },
        relations = AniSubjectRelations(subjectId.toLong(), emptyList(), emptyList(), emptyList(), emptyList()),
        collectionType = type,
        updatedAt = "2024-01-02T00:00:00Z",
    )

    /**
     * 像收藏页一样持续收集分页器: Room 失效时分页器发出新一代 [androidx.paging.PagingData], 这里跟着切换, 快照始终是最新一代.
     * 分页器的 RemoteMediator 只在创建时判断是否从服务端刷新, 所以能验证 "已在展示的列表" 对失效的反应.
     */
    private class PagerProbe(context: CoroutineContext) : PagingDataPresenter<SubjectCollectionInfo>(mainContext = context) {
        /**
         * 收到的分页事件数. Room 失效后分页器发出新一代数据, 至少带来一个 Refresh 事件.
         */
        val events = AtomicInteger(0)

        override suspend fun presentPagingDataEvent(event: PagingDataEvent<SubjectCollectionInfo>) {
            events.incrementAndGet()
        }

        val items: List<SubjectCollectionInfo> get() = snapshot().items

        /**
         * 等待快照满足 [predicate] (真实时间, 最多 10 秒).
         */
        suspend fun awaitItems(predicate: (List<SubjectCollectionInfo>) -> Boolean): List<SubjectCollectionInfo> =
            withTimeout(10.seconds) {
                var current = items
                while (!predicate(current)) {
                    delay(20)
                    current = items
                }
                current
            }

        /**
         * 等待事件数超过 [before] (即分页器已经因 Room 失效重新加载了一代).
         */
        suspend fun awaitEventsAfter(before: Int) = withTimeout(10.seconds) {
            while (events.get() <= before) delay(20)
        }
    }

    /**
     * 等待 fake 服务端已开始的调用数达到 [count] (真实时间, 最多 10 秒).
     */
    private suspend fun FakeSubjectService.awaitStarted(count: Int) = withTimeout(10.seconds) {
        while (started.get() < count) delay(10)
    }

    /**
     * 在当前作用域内持续收集 [CollectionsFilterQuery.type] 为 DOING 的分页器, 返回探针; 调用方负责取消 [Job].
     */
    private suspend fun Fixture.collectDoingPager(): Pair<PagerProbe, Job> {
        // 只传调度器, 不带 Job: 否则 presenter 内的 withContext 会脱离收集协程, 取消不了.
        val probe = PagerProbe(coroutineContext.minusKey(Job))
        val job = CoroutineScope(coroutineContext).launch {
            repository.subjectCollectionsPager(CollectionsFilterQuery(UnifiedCollectionType.DOING))
                .collectLatest { probe.collectFrom(it) }
        }
        return probe to job
    }

    @Suppress("DEPRECATION")
    private fun episode(
        subjectId: Int,
        episodeId: Int,
        sort: Int,
        lastFetched: Long,
    ) = EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId,
        episodeType = EpisodeType.MainStory,
        name = "ep",
        nameCn = "第 $sort 集",
        airDate = PackedDate.Invalid,
        comment = 0,
        desc = "",
        sort = EpisodeSort(sort),
        sortNumber = sort.toFloat(),
        selfCollectionType = UnifiedCollectionType.DONE,
        lastFetched = lastFetched,
    )

    // region invalidateCache

    @Test
    fun `INV-01 服务端没有该条目 (404) 时 invalidateCache 删除本地行并保留其他条目`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now), subject(3, now)))

        repository.invalidateCache(listOf(1, 3))

        assertNull(dao.findById(1).first())
        assertNull(dao.findById(3).first())
        assertNotNull(dao.findById(2).first())

        assertNull(repository.getSubjectCollectionTypeOffline(1).first())
        assertEquals(UnifiedCollectionType.DOING, repository.getSubjectCollectionTypeOffline(2).first())
        assertNull(repository.getSubjectDisplayInfoOffline(3).first())
    }

    @Test
    fun `INV-02 invalidateCache 将剩余条目的 lastFetched 置 0 但保留其他字段`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now, type = UnifiedCollectionType.DONE, score = 8), subject(2, now)))

        repository.invalidateCache(listOf(2))

        val remaining = assertNotNull(dao.findById(1).first())
        assertEquals(0, remaining.lastFetched)
        assertEquals(UnifiedCollectionType.DONE, remaining.collectionType)
        assertEquals(8, remaining.selfRatingInfo.score)
        assertEquals("条目 1", remaining.nameCn)
        assertEquals(1L, remaining.lastUpdated)

        // 分页器依据最新的 lastFetched 判断是否刷新
        assertEquals(0, dao.lastFetched(null))
        assertEquals(0, dao.lastFetched(UnifiedCollectionType.DONE))
    }

    @Test
    fun `INV-03 invalidateCache 级联删除被删条目的剧集缓存, 保留其他条目的剧集`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))
        database.episodeCollection().upsert(
            listOf(
                episode(1, 11, sort = 1, lastFetched = now),
                episode(1, 12, sort = 2, lastFetched = now),
                episode(2, 21, sort = 1, lastFetched = now),
            ),
        )

        repository.invalidateCache(listOf(1))

        assertEquals(emptyList(), database.episodeCollection().listIdBySubjectId(1).first())
        assertEquals(listOf(21), database.episodeCollection().listIdBySubjectId(2).first())
    }

    @Test
    fun `INV-04 invalidateCache 空列表不做任何事`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))

        repository.invalidateCache(emptyList())

        assertEquals(now, assertNotNull(dao.findById(1).first()).lastFetched)
        assertEquals(now, assertNotNull(dao.findById(2).first()).lastFetched)
        assertEquals(now, dao.lastFetched(null))
    }

    @Test
    fun `INV-05 invalidateCache 未知 id 不报错, 仍重置 lastFetched`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now)))

        repository.invalidateCache(listOf(999))

        assertEquals(0, assertNotNull(dao.findById(1).first()).lastFetched)
    }

    @Test
    fun `INV-06 invalidateCache 可重复调用`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))

        repository.invalidateCache(listOf(1))
        repository.invalidateCache(listOf(1))
        repository.invalidateCache(listOf(2))

        assertNull(dao.findById(1).first())
        assertNull(dao.findById(2).first())
        assertEquals(0, dao.lastFetched(null))
    }

    @Test
    fun `INV-12 服务端仍有收藏时用服务端的值覆盖本地行与剧集, 不删除`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now, type = UnifiedCollectionType.DOING, score = 3), subject(2, now)))
        database.episodeCollection().upsert(
            listOf(
                episode(1, 11, sort = 1, lastFetched = now),
                episode(1, 12, sort = 2, lastFetched = now),
            ),
        )
        service.serverSubjects[1] = serverSubject(1, type = AniCollectionType.DONE, score = 9, episodeIds = listOf(11, 13))

        repository.invalidateCache(listOf(1))

        val row = assertNotNull(dao.findById(1).first(), "row must be refetched, not deleted")
        assertEquals(UnifiedCollectionType.DONE, row.collectionType)
        assertEquals(9, row.selfRatingInfo.score)
        assertEquals("条目 1 (服务端)", row.nameCn)
        // 重新拉取后仍统一置 0: 下次创建分页器时整体刷新
        assertEquals(0, row.lastFetched)
        // 剧集按服务端更新: 12 被删, 13 新增
        assertEquals(listOf(11, 13), database.episodeCollection().listIdBySubjectId(1).first().sorted())

        val untouched = assertNotNull(dao.findById(2).first())
        assertEquals(UnifiedCollectionType.DOING, untouched.collectionType)
        assertEquals(0, untouched.lastFetched)
        assertEquals(listOf(1), service.fetchedSubjectIds)
    }

    @Test
    fun `INV-13 服务端条目存在但未收藏时删除本地行`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))
        database.episodeCollection().upsert(listOf(episode(1, 11, sort = 1, lastFetched = now)))
        service.serverSubjects[1] = serverSubject(1, type = null)

        repository.invalidateCache(listOf(1))

        assertNull(dao.findById(1).first())
        assertEquals(emptyList(), database.episodeCollection().listIdBySubjectId(1).first())
        assertEquals(0, assertNotNull(dao.findById(2).first()).lastFetched)
        assertEquals(listOf(1), service.fetchedSubjectIds)
    }

    @Test
    fun `INV-14 网络失败时保留本地行, 只将 lastFetched 置 0`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now, type = UnifiedCollectionType.DONE, score = 8), subject(2, now)))
        database.episodeCollection().upsert(listOf(episode(1, 11, sort = 1, lastFetched = now)))
        service.serverSubjects[1] = serverSubject(1, score = 9)
        service.failingSubjectIds += 1

        repository.invalidateCache(listOf(1))

        val row = assertNotNull(dao.findById(1).first(), "row must never be deleted on failure")
        assertEquals(0, row.lastFetched)
        assertEquals(UnifiedCollectionType.DONE, row.collectionType)
        assertEquals(8, row.selfRatingInfo.score)
        assertEquals(listOf(11), database.episodeCollection().listIdBySubjectId(1).first())
        assertEquals(0, assertNotNull(dao.findById(2).first()).lastFetched)
        assertEquals(listOf(1), service.fetchedSubjectIds)
    }

    @Test
    fun `INV-15 部分条目失败不影响已在途的其他条目的重新拉取`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now), subject(3, now)))
        service.serverSubjects[1] = serverSubject(1, score = 9)
        service.serverSubjects[2] = serverSubject(2, score = 7)
        service.failingSubjectIds += 1
        // 三个拉取并行在途 (并行度 4), 放行后 1 失败: 已在途的 2 / 3 照常完成
        val gate = CompletableDeferred<Unit>()
        service.gate = gate

        val job = CoroutineScope(coroutineContext).launch { repository.invalidateCache(listOf(1, 2, 3)) }
        service.awaitStarted(3)
        gate.complete(Unit)
        job.join()

        // 1 失败保留旧值; 2 成功更新; 3 服务端没有, 删除
        assertEquals(0, assertNotNull(dao.findById(1).first()).selfRatingInfo.score)
        assertEquals(7, assertNotNull(dao.findById(2).first()).selfRatingInfo.score)
        assertNull(dao.findById(3).first())
        assertEquals(setOf(1, 2, 3), service.fetchedSubjectIds.toSet())
        assertEquals(0, dao.lastFetched(null))
    }

    @Test
    fun `INV-21 重新拉取并行进行, 并行度不超过 4`() = runRepositoryTest {
        val now = currentTimeMillis()
        val ids = (1..8).toList()
        dao.upsert(ids.map { subject(it, now) })
        ids.forEach { service.serverSubjects[it] = serverSubject(it, score = 9) }
        val gate = CompletableDeferred<Unit>()
        service.gate = gate

        val job = CoroutineScope(coroutineContext).launch { repository.invalidateCache(ids) }
        service.awaitStarted(4)
        // 前 4 个在途时第 5 个必须等待 (若不限并行度, 8 个会立刻全部发出)
        delay(200)
        assertEquals(4, service.started.get())
        assertEquals(4, service.inFlight.get())

        gate.complete(Unit)
        job.join()
        assertEquals(8, service.started.get())
        assertEquals(4, service.maxInFlight.get())
        assertEquals(ids.toSet(), service.fetchedSubjectIds.toSet())
        for (id in ids) {
            val row = assertNotNull(dao.findById(id).first())
            assertEquals(9, row.selfRatingInfo.score)
            assertEquals(0, row.lastFetched)
        }
    }

    @Test
    fun `INV-22 首次拉取失败后不再发起新的拉取, 已在途的照常完成, 未拉取的行保留并置 0`() = runRepositoryTest {
        val now = currentTimeMillis()
        val ids = (1..8).toList()
        dao.upsert(ids.map { subject(it, now) })
        ids.forEach { service.serverSubjects[it] = serverSubject(it, score = 9) }
        val gate = CompletableDeferred<Unit>()
        val failGate = CompletableDeferred<Unit>()
        service.gate = gate
        service.failFirstFetchGate = failGate

        val job = CoroutineScope(coroutineContext).launch { repository.invalidateCache(ids) }
        // 4 个在途: 1 个等待失败, 3 个等待放行
        service.awaitStarted(4)
        // 断网: 第一个失败, 它释放的名额不再用来发起新的拉取
        failGate.complete(Unit)
        delay(200)
        assertEquals(4, service.started.get())

        gate.complete(Unit)
        job.join()
        assertEquals(4, service.started.get(), "no new fetch after the first failure")
        val failedId = assertNotNull(service.failedSubjectId)
        val fetched = service.fetchedSubjectIds.toSet()
        assertEquals(4, fetched.size)
        assertTrue(failedId in fetched)
        for (id in ids) {
            val row = assertNotNull(dao.findById(id).first(), "row $id must be kept")
            assertEquals(0, row.lastFetched)
            // 失败的与未拉取的保留旧值; 在途完成的 3 个是服务端的新值
            assertEquals(if (id in fetched && id != failedId) 9 else 0, row.selfRatingInfo.score, "subject $id")
        }
    }

    // endregion

    // region invalidateAllCaches

    @Test
    fun `INV-07 invalidateAllCaches 保留所有条目与剧集, 只将 lastFetched 置 0`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(
            listOf(
                subject(1, now, type = UnifiedCollectionType.WISH),
                subject(2, now, type = UnifiedCollectionType.DOING, score = 7),
                subject(3, now - 1000, type = UnifiedCollectionType.DONE),
            ),
        )
        database.episodeCollection().upsert(
            listOf(
                episode(1, 11, sort = 1, lastFetched = now),
                episode(1, 12, sort = 2, lastFetched = now),
            ),
        )

        repository.invalidateAllCaches()

        for (id in listOf(1, 2, 3)) {
            val entity = assertNotNull(dao.findById(id).first(), "subject $id should be kept")
            assertEquals(0, entity.lastFetched)
        }
        assertEquals(UnifiedCollectionType.WISH, assertNotNull(dao.findById(1).first()).collectionType)
        assertEquals(7, assertNotNull(dao.findById(2).first()).selfRatingInfo.score)
        assertEquals(listOf(11, 12), database.episodeCollection().listIdBySubjectId(1).first())

        assertEquals(0, dao.lastFetched(null))
        assertEquals(0, dao.lastFetched(UnifiedCollectionType.WISH))
        assertEquals(0, dao.lastFetched(UnifiedCollectionType.DOING))
        assertEquals(0, dao.lastFetched(UnifiedCollectionType.DONE))
    }

    @Test
    fun `INV-08 invalidateAllCaches 空表不报错`() = runRepositoryTest {
        repository.invalidateAllCaches()

        assertEquals(0, dao.lastFetched(null))
        assertNull(dao.findById(1).first())
    }

    // endregion

    // region 失效后重新拉取

    @Test
    fun `INV-09 未失效的缓存不会触发网络拉取`() = runRepositoryTest {
        dao.upsert(subject(1, currentTimeMillis()))

        repository.subjectCollectionFlow(1).test {
            assertEquals(1, awaitItem().subjectId)
            delay(200)
            assertTrue(service.fetchedSubjectIds.isEmpty(), "unexpected fetch: ${service.fetchedSubjectIds}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `INV-10 invalidateAllCaches 后条目页先展示缓存再从服务端重新拉取`() = runRepositoryTest {
        dao.upsert(subject(1, currentTimeMillis()))

        repository.invalidateAllCaches()

        repository.subjectCollectionFlow(1).test {
            // 过期缓存仍先展示, 保证离线可用
            assertEquals(1, awaitItem().subjectId)
            assertEquals(1, withTimeout(5.seconds) { service.firstFetch.await() })
            assertEquals(listOf(1), service.fetchedSubjectIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `INV-11 invalidateCache 后未被删除的条目也会重新拉取`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))

        repository.invalidateCache(listOf(2))
        // 失效时已重新拉取过 2 (服务端没有 → 删除)
        assertEquals(listOf(2), service.fetchedSubjectIds)

        repository.subjectCollectionFlow(1).test {
            assertEquals(1, awaitItem().subjectId)
            withTimeout(5.seconds) {
                while (service.fetchedSubjectIds.size < 2) delay(20)
            }
            assertEquals(listOf(2, 1), service.fetchedSubjectIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region 已在展示的收藏列表

    @Test
    fun `INV-16 已在展示的分页器 - invalidateCache 后条目仍在列表中且为服务端的新值`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now, score = 3), subject(2, now)))
        service.serverSubjects[1] = serverSubject(1, type = AniCollectionType.DOING, score = 9, episodeIds = listOf(11))
        service.serverSubjects[2] = serverSubject(2)

        val (probe, job) = collectDoingPager()
        try {
            val before = probe.awaitItems { items -> items.map { it.subjectId }.toSet() == setOf(1, 2) }
            assertEquals(3, before.single { it.subjectId == 1 }.selfRatingInfo.score)

            repository.invalidateCache(listOf(1))

            // 分页器没有重建 (RemoteMediator 不会再刷新), 但 Room 失效后重新加载, 条目 1 必须还在且是新值
            val after = probe.awaitItems { items -> items.singleOrNull { it.subjectId == 1 }?.selfRatingInfo?.score == 9 }
            assertEquals(setOf(1, 2), after.map { it.subjectId }.toSet())
            assertEquals(UnifiedCollectionType.DOING, after.single { it.subjectId == 1 }.collectionType)
            assertEquals(listOf(11), after.single { it.subjectId == 1 }.episodes.map { it.episodeId })
            assertEquals(listOf(1), service.fetchedSubjectIds)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `INV-17 已在展示的分页器 - 服务端已无收藏时条目从列表中消失`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now), subject(2, now)))
        service.serverSubjects[1] = serverSubject(1, type = null)
        service.serverSubjects[2] = serverSubject(2)

        val (probe, job) = collectDoingPager()
        try {
            probe.awaitItems { items -> items.map { it.subjectId }.toSet() == setOf(1, 2) }

            repository.invalidateCache(listOf(1))

            val after = probe.awaitItems { items -> items.map { it.subjectId } == listOf(2) }
            assertEquals(listOf(2), after.map { it.subjectId })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `INV-18 已在展示的分页器 - 网络失败时条目保留在列表中`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(listOf(subject(1, now, score = 3), subject(2, now)))
        service.failingSubjectIds += 1

        val (probe, job) = collectDoingPager()
        try {
            probe.awaitItems { items -> items.map { it.subjectId }.toSet() == setOf(1, 2) }
            val eventsBefore = probe.events.get()

            repository.invalidateCache(listOf(1))

            // resetAllLastFetched 改写了两行, Room 失效后分页器重新加载一代; 等到这一代到达再断言, 不靠固定等待
            assertEquals(listOf(1), service.fetchedSubjectIds)
            probe.awaitEventsAfter(eventsBefore)
            assertEquals(setOf(1, 2), probe.items.map { it.subjectId }.toSet())
            assertEquals(3, probe.items.single { it.subjectId == 1 }.selfRatingInfo.score)
        } finally {
            job.cancel()
        }
    }

    // endregion

    // region 失效事件

    @Test
    fun `INV-19 两种失效完成后都发出 collectionsInvalidated, 空列表不发出`() = runRepositoryTest {
        dao.upsert(subject(1, currentTimeMillis()))

        repository.collectionsInvalidated.test {
            repository.invalidateCache(listOf(1))
            awaitItem()

            repository.invalidateAllCaches()
            awaitItem()

            repository.invalidateCache(emptyList())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `INV-20 重新拉取失败时仍发出 collectionsInvalidated`() = runRepositoryTest {
        val now = currentTimeMillis()
        dao.upsert(subject(1, now))
        service.failingSubjectIds += 1

        repository.collectionsInvalidated.test {
            repository.invalidateCache(listOf(1))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        // 行保留, lastFetched 已置 0
        assertEquals(0, assertNotNull(dao.findById(1).first()).lastFetched)
        assertEquals(0, dao.lastFetched(null))
    }

    // endregion
}
