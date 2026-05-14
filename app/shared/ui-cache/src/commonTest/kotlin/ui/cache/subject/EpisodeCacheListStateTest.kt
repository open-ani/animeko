/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequesterImpl
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EpisodeCacheListStateTest {
    @Test
    fun `requesting another episode should cancel previous selector request`() = runComposeStateTest {
        val requester1 = createRequester()
        val requester2 = createRequester()
        val episode1 = createEpisodeState(1, requester1, this)
        val episode2 = createEpisodeState(2, requester2, this)
        val episodesState = mutableStateOf(listOf(episode1, episode2))
        val currentEpisodeState = object : androidx.compose.runtime.State<EpisodeCacheState?> {
            override val value: EpisodeCacheState?
                get() = episodesState.value.firstOrNull { it.currentStage is CacheRequestStage.Working }
        }
        val state = EpisodeCacheListStateImpl(
            episodes = episodesState,
            currentEpisode = currentEpisodeState,
            onRequestCache = { episode, _ ->
                episode.cacheRequester.request(testRequest(episode.episodeId))
            },
            onRequestCacheComplete = {},
            onDeleteCache = {},
            onBindLocalFile = { _, _ -> error("not used") },
        )

        state.requestCache(episode1, autoSelectCached = false)
        takeSnapshot()

        assertSame(episode1, state.currentEpisode)
        assertTrue(requester1.stage.value is CacheRequestStage.Working)

        state.requestCache(episode2, autoSelectCached = false)
        takeSnapshot()

        assertEquals(CacheRequestStage.Idle, requester1.stage.value)
        assertSame(episode2, state.currentEpisode)
        assertTrue(requester2.stage.value is CacheRequestStage.Working)
    }

    private fun createEpisodeState(
        episodeId: Int,
        requester: EpisodeCacheRequester,
        backgroundScope: kotlinx.coroutines.CoroutineScope,
    ): EpisodeCacheState {
        return EpisodeCacheState(
            episodeId = episodeId,
            cacheRequester = requester,
            currentStageState = object : androidx.compose.runtime.State<CacheRequestStage> {
                override val value: CacheRequestStage
                    get() = requester.stage.value
            },
            infoState = mutableStateOf(
                EpisodeCacheInfo(
                    sort = EpisodeInfo.Empty.sort,
                    ep = EpisodeInfo.Empty.ep,
                    title = "Episode $episodeId",
                    watchStatus = EpisodeCacheInfo.Placeholder.watchStatus,
                    hasPublished = true,
                ),
            ),
            cacheStatusState = mutableStateOf(EpisodeCacheStatus.NotCached),
            backgroundScope = backgroundScope,
        )
    }

    private fun createRequester(): EpisodeCacheRequester {
        return EpisodeCacheRequesterImpl(
            mediaFetcherLazy = flowOf(FakeMediaFetcher),
            mediaSelectorFactory = FakeMediaSelectorFactory,
            storagesLazy = flowOf(emptyList()),
        )
    }

    private fun testRequest(episodeId: Int): EpisodeCacheRequest {
        return EpisodeCacheRequest(
            subjectInfo = SubjectInfo.Empty.copy(subjectId = 1, name = "subject", nameCn = "subject"),
            episodeInfo = EpisodeInfo.Empty.copy(episodeId = episodeId),
        )
    }
}

private object FakeMediaFetcher : MediaFetcher {
    override fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: kotlin.coroutines.CoroutineContext,
    ): MediaFetchSession {
        return FakeMediaFetchSession
    }
}

private object FakeMediaSelectorFactory : MediaSelectorFactory {
    override fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: kotlin.coroutines.CoroutineContext,
    ): MediaSelector {
        return TestMediaSelector
    }
}

private object FakeMediaFetchSession : MediaFetchSession {
    override val request: Flow<MediaFetchRequest> = emptyFlow()
    override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
    override val cumulativeResults: Flow<List<Media>> = flowOf(emptyList())
    override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)

    override fun setFetchRequest(request: MediaFetchRequest) = Unit
}

private val TestMediaSelector = DefaultMediaSelector(
    mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
    mediaListNotCached = flowOf(emptyList()),
    savedUserPreference = flowOf(MediaPreference.Empty),
    savedDefaultPreference = flowOf(MediaPreference.Empty),
    mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
    enableCaching = false,
)
