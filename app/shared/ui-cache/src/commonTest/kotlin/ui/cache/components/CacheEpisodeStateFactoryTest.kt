/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CacheEpisodeStateFactoryTest {
    @Test
    fun `pending playability does not hide downloading caches or block other rows`() = runComposeStateTest {
        val pending = TestCacheWithPendingPlayability(1)
        val completed = TestCacheWithPendingPlayability(2).apply {
            state.value = MediaCacheState.COMPLETED
            canPlay.emit(true)
        }
        val scope = object : HasBackgroundScope {
            override val backgroundScope: CoroutineScope = this@runComposeStateTest.backgroundScope
        }
        var entries: List<CacheEpisodeState>? = null
        backgroundScope.launch {
            combine(
                listOf(pending, completed).map { cache ->
                    scope.createCacheEpisodeStateFlow(
                        groupId = "1",
                        mediaCache = CacheWithEngine(cache, MediaCacheEngineKey.WebM3u),
                        subjectCollectionType = flowOf(UnifiedCollectionType.DOING),
                        playbackHistoriesByEpisodeId = flowOf(emptyMap()),
                    )
                },
            ) { it.toList() }.collect { entries = it }
        }
        testScheduler.runCurrent()

        fun downloading() = assertNotNull(entries).single { it.status == CacheStatusFilter.Downloading }
        assertEquals(listOf(1, 2), assertNotNull(entries).map { it.episodeId })
        assertEquals(CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED, downloading().playability)
        assertEquals(CacheEpisodePaused.IN_PROGRESS, downloading().state)

        // Download state and progress must keep updating even before canPlay emits.
        pending.state.value = MediaCacheState.PAUSED
        pending.fileStats.value = MediaCache.FileStats(100.megaBytes, 50.megaBytes, 0.5f.toProgress())
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        assertEquals(CacheEpisodePaused.PAUSED, downloading().state)
        assertEquals(0.5f.toProgress(), downloading().progress)

        pending.canPlay.emit(false)
        testScheduler.runCurrent()
        assertEquals(CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED, downloading().playability)
        pending.canPlay.emit(true)
        testScheduler.runCurrent()
        assertEquals(CacheEpisodeState.Playability.PLAYABLE, downloading().playability)

        pending.state.value = MediaCacheState.COMPLETED
        testScheduler.runCurrent()
        assertEquals(listOf(CacheStatusFilter.Finished, CacheStatusFilter.Finished), assertNotNull(entries).map { it.status })
    }
}

internal class TestCacheWithPendingPlayability(episodeId: Int) : TestMediaCache(
    media = TestMediaList.first().let { CachedMedia(it, "local-cache", it.download) },
    metadata = MediaCacheMetadata(
        subjectId = "1",
        episodeId = episodeId.toString(),
        subjectNameCN = "subject",
        subjectNames = listOf("subject"),
        episodeSort = EpisodeSort(episodeId),
        episodeEp = EpisodeSort(episodeId),
        episodeName = "episode $episodeId",
    ),
) {
    override val canPlay = MutableSharedFlow<Boolean>(replay = 1)
}
