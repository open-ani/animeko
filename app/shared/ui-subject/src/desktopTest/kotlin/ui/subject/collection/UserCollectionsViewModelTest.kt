/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.AnalyticsSettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.AniDatabaseConstructor
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * 覆盖 [UserCollectionsViewModel] 构造时启动的后台收集 (Kotlin `init {}` 块):
 * 本 ViewModel 由 androidx `viewModel {}` 取得, 不会被 compose remember, 所以
 * [me.him188.ani.app.ui.foundation.AbstractViewModel.init] 永远不会执行. 这里不 remember、不调用 `onRemembered`, 直接构造后验证:
 * - 仓库发出 `collectionsInvalidated` → 分页器被重建 (分页器工厂再次被调用), 各类型收藏数量流被重新收集;
 * - `SessionEvent.NewLogin` → 同样刷新.
 *
 * 用 Koin 提供 fake 仓库; [EpisodeProgressRepository] 是 final 类, 构造它需要真实的 [EpisodeCollectionRepository], 用内存 Room 库.
 */
@OptIn(ExperimentalCoroutinesApi::class, TestOnly::class)
class UserCollectionsViewModelTest {

    /**
     * 记录分页器工厂与数量流被调用的次数; 其余方法不应被调用.
     */
    private class FakeSubjectCollectionRepository : SubjectCollectionRepository() {
        /** [subjectCollectionsPager] 被调用 (分页器被创建 / 重建) 的次数. */
        val pagerCalls = AtomicInteger(0)

        /** [subjectCollectionCountsFlow] 返回的流被收集的次数. */
        val countsCollected = AtomicInteger(0)

        fun invalidate() = notifyCollectionsInvalidated()

        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> {
            pagerCalls.incrementAndGet()
            return flowOf(PagingData.empty())
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = flow {
            countsCollected.incrementAndGet()
            emit(null)
        }

        override suspend fun invalidateAllCaches() = invalidate()

        override suspend fun invalidateCache(subjectIds: List<Int>) = invalidate()

        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
            throw UnsupportedOperationException()

        override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()

        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ) = throw UnsupportedOperationException()

        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ) = throw UnsupportedOperationException()

        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
            throw UnsupportedOperationException()

        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
            throw UnsupportedOperationException()

        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            throw UnsupportedOperationException()

        override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
    }

    private class FakeSessionStateProvider : SessionStateProvider {
        val events = MutableSharedFlow<SessionEvent>()
        override val stateFlow: Flow<SessionState> = MutableStateFlow(SessionState.Valid(bangumiConnected = true))
        override val eventFlow: Flow<SessionEvent> = events
    }

    /**
     * 只提供 ViewModel 构造时读取的 [uiSettings], 其余访问即报错.
     */
    private class FakeSettingsRepository : SettingsRepository {
        override val uiSettings: Settings<UISettings> = object : Settings<UISettings> {
            private val state = MutableStateFlow(UISettings.Default)
            override val flow: Flow<UISettings> = state
            override suspend fun set(value: UISettings) {
                state.value = value
            }
        }

        override val danmakuEnabled: Settings<Boolean> by lazy { error("not implemented") }
        override val danmakuConfig: Settings<DanmakuConfig> by lazy { error("not implemented") }
        override val danmakuFilterConfig: Settings<DanmakuFilterConfig> by lazy { error("not implemented") }
        override val mediaSelectorSettings: Settings<MediaSelectorSettings> by lazy { error("not implemented") }
        override val defaultMediaPreference: Settings<MediaPreference> by lazy { error("not implemented") }
        override val profileSettings: Settings<ProfileSettings> by lazy { error("not implemented") }
        override val proxySettings: Settings<ProxySettings> by lazy { error("not implemented") }
        override val mediaCacheSettings: Settings<MediaCacheSettings> by lazy { error("not implemented") }
        override val danmakuSettings: Settings<DanmakuSettings> by lazy { error("not implemented") }
        override val themeSettings: Settings<ThemeSettings> by lazy { error("not implemented") }
        override val updateSettings: Settings<UpdateSettings> by lazy { error("not implemented") }
        override val videoScaffoldConfig: Settings<VideoScaffoldConfig> by lazy { error("not implemented") }
        override val playerKernelConfig: Settings<PlayerKernelConfig> by lazy { error("not implemented") }
        override val videoResolverSettings: Settings<VideoResolverSettings> by lazy { error("not implemented") }
        override val anitorrentConfig: Settings<AnitorrentConfig> by lazy { error("not implemented") }
        override val pikpakConfig: Settings<PikPakConfig> by lazy { error("not implemented") }
        override val torrentPeerConfig: Settings<TorrentPeerConfig> by lazy { error("not implemented") }
        override val oneshotActionConfig: Settings<OneshotActionConfig> by lazy { error("not implemented") }
        override val analyticsSettings: Settings<AnalyticsSettings> by lazy { error("not implemented") }
        override val debugSettings: Settings<DebugSettings> by lazy { error("not implemented") }
        override val watchTogetherSettings: Settings<WatchTogetherSettings> by lazy { error("not implemented") }
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

    private lateinit var database: AniDatabase
    private lateinit var repository: FakeSubjectCollectionRepository
    private lateinit var sessionStateProvider: FakeSessionStateProvider
    private val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        // produceState / 分页器展示器要在 Main 上更新 compose state
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder<AniDatabase> { AniDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        repository = FakeSubjectCollectionRepository()
        sessionStateProvider = FakeSessionStateProvider()

        val animeScheduleRepository = AnimeScheduleRepository(AnimeScheduleService(UnusedScheduleApi))
        val episodeCollectionRepository = EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            episodeService = EpisodeServiceImpl(UnusedSubjectsApi),
            animeScheduleRepository = animeScheduleRepository,
            subjectCollectionRepository = lazy { repository },
            getEpisodeTypeFiltersUseCase = GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) },
        )
        val episodeProgressRepository = EpisodeProgressRepository(
            episodeCollectionRepository,
            object : MediaCacheManager(emptyList(), fixtureScope) {},
        )
        startKoin {
            modules(
                module {
                    single<SubjectCollectionRepository> { repository }
                    single<SessionStateProvider> { sessionStateProvider }
                    single<SettingsRepository> { FakeSettingsRepository() }
                    single<EpisodeProgressRepository> { episodeProgressRepository }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        fixtureScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    /**
     * 像收藏页一样持续收集第一个 tab 的分页器 (不经过 compose): 只有被收集时分页器工厂才会被调用.
     */
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    private fun CoroutineScope.collectFirstTab(vm: UserCollectionsViewModel): Job {
        val items = vm.state.getCollectionLazyPagingItems(0)
        return launch { items.collectPagingData() }
    }

    private suspend fun awaitAtLeast(expected: Int, counter: AtomicInteger, what: String) = withTimeout(10.seconds) {
        while (counter.get() < expected) delay(20)
        assertEquals(expected, counter.get(), what)
    }

    private fun runViewModelTest(block: suspend CoroutineScope.(UserCollectionsViewModel) -> Unit) = runBlocking {
        // 不 remember, 不调用 onRemembered: 与 MainScreen 里 viewModel { UserCollectionsViewModel() } 一致
        val vm = UserCollectionsViewModel()
        try {
            val collector = collectFirstTab(vm)
            try {
                awaitAtLeast(1, repository.pagerCalls, "pager created once on first collection")
                awaitAtLeast(1, repository.countsCollected, "counts collected once at construction")
                block(vm)
            } finally {
                collector.cancel()
            }
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun `COLL-VM-01 collectionsInvalidated 时重建分页器并重新拉取数量 - 构造即订阅, 无需 remember`() = runViewModelTest { _ ->
        // 构造时就已订阅 (而不是 onRemembered 时)
        withTimeout(10.seconds) { repository.collectionsInvalidatedSubscriptionCount.first { it >= 1 } }
        assertEquals(1, repository.pagerCalls.get())
        assertEquals(1, repository.countsCollected.get())

        repository.invalidate()

        awaitAtLeast(2, repository.pagerCalls, "pager rebuilt after invalidation")
        awaitAtLeast(2, repository.countsCollected, "counts re-collected after invalidation")
    }

    @Test
    fun `COLL-VM-02 NewLogin 时重建分页器并重新拉取数量`() = runViewModelTest { _ ->
        withTimeout(10.seconds) { sessionStateProvider.events.subscriptionCount.first { it >= 1 } }
        assertEquals(1, repository.pagerCalls.get())

        sessionStateProvider.events.emit(SessionEvent.NewLogin)

        awaitAtLeast(2, repository.pagerCalls, "pager rebuilt after new login")
        awaitAtLeast(2, repository.countsCollected, "counts re-collected after new login")
    }
}
