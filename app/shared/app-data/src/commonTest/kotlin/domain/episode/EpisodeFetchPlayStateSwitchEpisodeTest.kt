/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.app.domain.player.extension.SwitchNextEpisodeExtension
import me.him188.ani.app.domain.player.extension.loadMedia
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.utils.coroutines.childScope
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 测试多个扩展之间的在 [EpisodeFetchSelectPlayState.switchEpisode] 的兼容性.
 */
class EpisodeFetchPlayStateSwitchEpisodeTest : AbstractPlayerExtensionTest() {
    private val playHistory = EpisodePlayHistoryRepositoryImpl(
        MemoryDataStore(EpisodeHistories.Empty),
        createMemoryPlaybackHistoryDao(),
        nowMillis = { 0 },
    )
    private val newEpisodeId = 1000

    private fun TestScope.createCase(): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<EpisodePlayHistoryRepository> { playHistory }
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }

        val rememberPlayProgress = EpisodePlayerExtensionFactory { context, koin ->
            RememberPlayProgressExtension(
                context,
                koin,
                periodicReportInterval = Duration.INFINITE,
            )
        }
        val state = suite.createState(
            listOf(
                rememberPlayProgress,
                SwitchNextEpisodeExtension.Factory(
                    getNextEpisode = { currentEpisodeId ->
                        assertEquals(initialEpisodeId, currentEpisodeId)
                        newEpisodeId
                    },
                ),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `switchEpisode then load play history - no media source`() = runTest {
        val (testScope, suite, state) =
            createCase()
        try {
            playHistory.saveOrUpdate(initialEpisodeId, 3000)
            playHistory.saveOrUpdate(newEpisodeId, 5000)

            // 播放到一半

            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

            // v2: 直接向播放器加载媒体, 绕过 fetch-select 管线. 这不会广播 MediaLoadedEvent,
            // 对扩展来说等同于"没有实际加载媒体源".
            suite.player.loadMedia(durationMs = 100_000, playWhenReady = false)
            advanceUntilIdle()

            // 播到最尾部了
            suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
            advanceUntilIdle()
            suite.player.injectEnded()
            advanceUntilIdle() // 不应自动切换到下一集数

            // 没有实际加载媒体源时，不会覆盖或删除旧进度
            assertEquals(3000, playHistory.getPositionMillisByEpisodeId(initialEpisodeId))

            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `switchEpisode then load play history - wait for media source`() = runTest {
        val (testScope, suite, state) =
            createCase()
        try {
            val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

            playHistory.saveOrUpdate(initialEpisodeId, 3000)
            playHistory.saveOrUpdate(newEpisodeId, 5000)

            // 播放到一半
            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

            val myMedia = TestMediaList[0]
            ms1.complete(listOf(myMedia))
            suite.setMediaDuration(100_000)
            state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
            advanceUntilIdle()

            assertEquals(3000, suite.player.currentPositionMillis.value)

            // 播到最尾部了
            suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
            advanceUntilIdle()
            suite.player.injectEnded()
            advanceUntilIdle() // 自动切换到下一集数

            // 前一集播放完毕了
            assertEquals(null, playHistory.getPositionMillisByEpisodeId(initialEpisodeId))

            assertEquals(newEpisodeId, state.getCurrentEpisodeId())

            val myMedia1 = TestMediaList[1]
            ms1.complete(listOf(myMedia1))
            suite.setMediaDuration(100_000)
            state.mediaSelectorFlow.filterNotNull().first().select(myMedia1)
            advanceUntilIdle() // 自动加载播放进度

            // should load the saved progress for new episode
            assertEquals(5000, suite.player.currentPositionMillis.value)
        } finally {
            testScope.cancel()
        }
    }
}
