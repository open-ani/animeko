/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 使用实际播放页状态验证加载回调、进度观察和页面生命周期的接线。 */
class PlaybackMemoryLifecycleTest : AbstractPlayerExtensionTest() {
    @Test
    fun `page records progress keeps experience across manual next and clears it on close`() = runTest {
        val scope = childScope()
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        val source = suite.mediaSelectorTestBuilder.delayedMediaSource("web-c", kind = MediaSourceKind.WEB)
        val original = suite.mediaSelectorTestBuilder.createMedia("web-c")
        val media = original.copy(properties = original.properties.copy(
            webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config"),
        ))
        source.complete(listOf(media))
        val state = EpisodeFetchSelectPlayState(
            subjectId, initialEpisodeId, suite.player, scope, emptyList(), suite.koin,
            mainDispatcher = EmptyCoroutineContext, playbackTimeSource = testScheduler.timeSource,
        )
        val memory = state.playbackSourceMemory
        val previouslyFailed = media.copy(mediaId = "web-a.1", mediaSourceId = "web-a")
        val previousAttempt = memory.beginAttempt(initialEpisodeId, previouslyFailed)
        memory.recordFailure(previousAttempt, PlaybackSourceMemory.FailureReason.RESOLUTION)
        memory.finishAttempt(previousAttempt)
        state.onUIReady()
        advanceUntilIdle()
        val selector = state.mediaSelectorFlow.filterNotNull().first()
        selector.selectAutomatically(media, expectedSelection = null)
        advanceUntilIdle()
        assertNull(memory.snapshot().recentSuccess)
        for (second in 1..5) {
            advanceTimeBy(1_000)
            suite.player.injectPosition(second * 1_000L)
            runCurrent()
        }
        assertTrue(memory.snapshot().isRecentSuccess(media))

        // 重试历史失败线路只恢复候选资格，不能打断正在正常播放的 C。
        val opened = suite.player.mediaData.value
        state.retryFailedPlaybackSources()
        runCurrent()
        assertSame(opened, suite.player.mediaData.value)
        assertEquals(5_000, suite.player.currentPositionMillis.value)
        assertTrue(memory.snapshot().failures.isEmpty())

        state.switchEpisode(initialEpisodeId + 1)
        advanceUntilIdle()
        assertTrue(memory.snapshot().isRecentSuccess(media))
        state.onClose()
        assertNull(memory.snapshot().recentSuccess)
        assertTrue(memory.snapshot().failures.isEmpty())
        scope.cancel()
    }
}
