/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.cache

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.requester.trySelectSingle
import me.him188.ani.app.domain.media.cache.storage.TestMediaCacheStorage
import me.him188.ani.app.domain.media.fetch.MediaFetcherConfig
import me.him188.ani.app.domain.media.fetch.MediaSourceMediaFetcher
import me.him188.ani.app.domain.media.framework.TestMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.junit.jupiter.api.TestInstance
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * @see EpisodeCacheState
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class EpisodeCacheStateTest {
    private val infoFlow = MutableStateFlow(
        EpisodeCacheInfo(
            sort = EpisodeSort(1),
            ep = EpisodeSort(1),
            title = "第一集的标题",
            watchStatus = UnifiedCollectionType.DOING,
            hasPublished = true,
        ),
    )
    private val cacheStatusFlow: MutableStateFlow<EpisodeCacheStatus> = MutableStateFlow(
        EpisodeCacheStatus.Cached(300.megaBytes),
    )

    private fun TestScope.createEpisodeCacheState(): EpisodeCacheState {
        val cacheRequester = createTestEpisodeCacheRequester()
        return EpisodeCacheState(
            1,
            cacheRequester = cacheRequester,
            currentStageState = cacheRequester.stage.produceState(scope = backgroundScope),
            infoState = infoFlow.produceState(scope = backgroundScope),
            cacheStatusState = cacheStatusFlow.produceState(scope = backgroundScope),
            backgroundScope = backgroundScope,
        ).apply {
            testScheduler.runCurrent() // start `launch` and `produceState`
        }
    }

    private fun createTestEpisodeCacheRequester(): EpisodeCacheRequester {
        return EpisodeCacheRequester(
            flowOf(
                MediaSourceMediaFetcher(
                    configProvider = { MediaFetcherConfig.Default },
                    mediaSources = listOf(
                        createTestMediaSourceInstance(
                            TestHttpMediaSource(
                                fetch = {
                                    SinglePagePagedSource {
                                        TestMediaList.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                                    }
                                },
                            ),
                        ),
                    ),
                    flowContext = EmptyCoroutineContext,
                ),
            ),
            mediaSelectorFactory = object : MediaSelectorFactory {
                override fun create(
                    subjectId: Int,
                    episodeId: Int,
                    mediaList: Flow<List<Media>>,
                    flowCoroutineContext: CoroutineContext
                ): MediaSelector =
                    TestMediaSelector(
                        mediaList.map { list ->
                            list.map {
                                MaybeExcludedMedia.Included(
                                    it,
                                    similarity = 90,
                                )
                            }
                        },
                    )
            },
            storagesLazy = flowOf(listOf(TestMediaCacheStorage())),
        )
    }

    @Test
    fun `initial state`() = runTest {
        val state = createEpisodeCacheState()
        testScheduler.advanceUntilIdle()
        Snapshot.current.dispose()

        assertEquals(infoFlow.value, state.info)
        assertEquals(false, state.isInfoLoading)
        assertEquals(CacheRequestStage.Idle, state.currentStage)
        assertIs<EpisodeCacheStatus.Cached>(state.cacheStatus)
        assertEquals(true, state.canCache)
        assertEquals(false, state.actionTasker.isRunning.first())
        assertEquals(false, state.showProgressIndicator.first())
    }

    ///////////////////////////////////////////////////////////////////////////
    // canCache
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `canCache is true for WISH`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.WISH)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    @Test
    fun `canCache is true for DOING`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.DOING)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    @Test
    fun `canCache is true for ON_HOLD`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.ON_HOLD)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    @Test
    fun `canCache is true for NOT_COLLECTED`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.NOT_COLLECTED)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    @Test
    fun `canCache is true for DONE`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.DONE)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    @Test
    fun `canCache is true for DROPPED`() = runTest {
        infoFlow.value = infoFlow.value.copy(watchStatus = UnifiedCollectionType.DROPPED)
        val state = createEpisodeCacheState()
        assertEquals(true, state.canCache)
    }

    ///////////////////////////////////////////////////////////////////////////
    // showProgressIndicator
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `showProgressIndicator is initially false`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        assertEquals(false, state.showProgressIndicator.first())
    }

    @Test
    fun `showProgressIndicator is true when actionTasker is running`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        val deferred = CompletableDeferred<Unit>()
        state.actionTasker.launch(start = CoroutineStart.UNDISPATCHED) {
            deferred.await()
        }
        takeSnapshot()

        assertEquals(true, state.showProgressIndicator.first())
        deferred.complete(Unit)
        state.actionTasker.join()

        takeSnapshot()
        assertEquals(false, state.showProgressIndicator.first())
    }

    @Test
    fun `showProgressIndicator is true when SelectMedia`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        state.cacheRequester
            .request(EpisodeCacheRequest(SubjectInfo.Empty, EpisodeInfo(1, EpisodeType.MainStory)))

        takeSnapshot()
        assertEquals(true, state.showProgressIndicator.first())
    }

    @Test
    fun `showProgressIndicator is true when SelectStorage`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        state.cacheRequester
            .request(EpisodeCacheRequest(SubjectInfo.Empty, EpisodeInfo(1, EpisodeType.MainStory)))
            .select(TestMediaList[0])

        takeSnapshot()

    }

    @Test
    fun `showProgressIndicator is false when Done`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        state.cacheRequester
            .request(EpisodeCacheRequest(SubjectInfo.Empty, EpisodeInfo(1, EpisodeType.MainStory)))
            .select(TestMediaList[0])
            .trySelectSingle()

        takeSnapshot()

        assertEquals(false, state.showProgressIndicator.first())
    }

    ///////////////////////////////////////////////////////////////////////////
    // currentSelectMediaTask
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `currentSelectMediaTask is null when no task`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        assertEquals(CacheRequestStage.Idle, state.currentStage)
    }

    @Test
    fun `currentSelectMediaTask is not null when SelectMedia`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        state.cacheRequester
            .request(EpisodeCacheRequest(SubjectInfo.Empty, EpisodeInfo(1, EpisodeType.MainStory)))

        takeSnapshot()

        assertIs<CacheRequestStage.SelectMedia>(state.currentStage)
    }

    @Test
    fun `currentStage is Done when Done`() = runComposeStateTest {
        val state = createEpisodeCacheState()
        state.cacheRequester
            .request(EpisodeCacheRequest(SubjectInfo.Empty, EpisodeInfo(1, EpisodeType.MainStory)))
            .select(TestMediaList[0])
            .trySelectSingle()

        takeSnapshot()
        runCurrent()

        assertIs<CacheRequestStage.Done>(state.currentStage)
    }
}
