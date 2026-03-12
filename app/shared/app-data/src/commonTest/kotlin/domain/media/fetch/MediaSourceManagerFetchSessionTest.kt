/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import kotlin.test.Test
import kotlin.test.assertSame

class MediaSourceManagerFetchSessionTest {
    @Test
    fun `request flow does not recreate session when media fetcher updates`() = kotlinx.coroutines.test.runTest {
        val fetcher1 = TestMediaFetcher("fetcher-1")
        val fetcher2 = TestMediaFetcher("fetcher-2")
        val manager = TestMediaSourceManager(fetcher1)
        val requestFlow = MutableStateFlow(TestRequest)

        requestFlow.mapLatest { manager.createFetchFetchSession(flowOf(it)) }.test {
            val initial = awaitItem() as TestMediaFetchSession
            assertSame(fetcher1, initial.owner)
            manager.mediaFetcherState.value = fetcher2
            testScheduler.runCurrent()
            expectNoEvents()

            requestFlow.value = TestRequest.copy(episodeId = "3")
            val recreated = awaitItem() as TestMediaFetchSession
            assertSame(fetcher2, recreated.owner)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new collection after media fetcher updates uses latest fetcher`() = kotlinx.coroutines.test.runTest {
        val fetcher1 = TestMediaFetcher("fetcher-1")
        val fetcher2 = TestMediaFetcher("fetcher-2")
        val manager = TestMediaSourceManager(fetcher1)

        val initial = manager.createFetchFetchSession(flowOf(TestRequest)) as TestMediaFetchSession
        assertSame(fetcher1, initial.owner)

        manager.mediaFetcherState.value = fetcher2

        val recreated = manager.createFetchFetchSession(flowOf(TestRequest)) as TestMediaFetchSession
        assertSame(fetcher2, recreated.owner)
    }

    private class TestMediaSourceManager(
        initialFetcher: MediaFetcher,
    ) : MediaSourceManager {
        val mediaFetcherState = MutableStateFlow(initialFetcher)

        override val allInstances: Flow<List<me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance>> =
            flowOf(emptyList())
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = mediaFetcherState
        override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader =
            MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)

        override suspend fun addInstance(
            instanceId: String,
            mediaSourceId: String,
            factoryId: FactoryId,
            config: MediaSourceConfig,
        ) = error("Not needed in test")

        override suspend fun getListBySubscriptionId(subscriptionId: String) = error("Not needed in test")
        override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("Not needed in test")
        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig) = error("Not needed in test")
        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("Not needed in test")
        override suspend fun removeInstance(instanceId: String) = error("Not needed in test")
        override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
    }

    private class TestMediaFetcher(
        val id: String,
    ) : MediaFetcher {
        override fun newSession(
            requestLazy: Flow<MediaFetchRequest>,
            flowContext: kotlin.coroutines.CoroutineContext,
        ): MediaFetchSession {
            return TestMediaFetchSession(this, requestLazy)
        }
    }

    private class TestMediaFetchSession(
        val owner: TestMediaFetcher,
        override val request: Flow<MediaFetchRequest>,
    ) : MediaFetchSession {
        override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
        override val cumulativeResults: Flow<List<Media>> = flowOf(emptyList())
        override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)

        override fun setFetchRequest(request: MediaFetchRequest) = Unit
    }

    private companion object {
        val TestRequest = MediaFetchRequest(
            subjectId = "1",
            episodeId = "2",
            subjectNames = listOf("Test"),
            episodeSort = me.him188.ani.datasources.api.EpisodeSort(1),
            episodeName = "EP1",
        )
    }
}
