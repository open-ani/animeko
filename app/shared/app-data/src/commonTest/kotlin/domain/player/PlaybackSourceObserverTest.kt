/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.platform.NetworkEnvironment
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSourceObserverTest {
    private val media = TestMediaList[0].copy(
        kind = MediaSourceKind.WEB,
        properties = TestMediaList[0].properties.copy(webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config-1")),
    )

    @Test
    fun `ready and paused time are not success but real progress is remembered and later errors revoke it`() = runTest {
        val memory = PlaybackSourceMemory()
        val observer = PlaybackSourceObserver(memory, testScheduler.timeSource)
        val attempt = memory.beginAttempt(1, media)
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        player.setMediaData(UriMediaData("https://site/video"), playWhenReady = true)
        val job = backgroundScope.launch { observer.observe(player, attempt) }
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertNull(memory.snapshot().recentSuccess)

        player.pause()
        runCurrent()
        advanceTimeBy(20_000)
        player.seekTo(100_000)
        runCurrent()
        assertNull(memory.snapshot().recentSuccess)

        player.play()
        runCurrent()
        for (second in 1..5) {
            advanceTimeBy(1_000)
            player.injectPosition(100_000L + second * 1_000)
            runCurrent()
            if (second < 5) assertNull(memory.snapshot().recentSuccess)
        }
        assertTrue(memory.snapshot().isRecentSuccess(media))
        player.injectError(PlaybackException(PlaybackErrorCode.IO, "test"))
        runCurrent()
        assertTrue(job.isCompleted)
        assertNull(memory.snapshot().recentSuccess)
        assertEquals(PlaybackSourceMemory.FailureReason.PLAYBACK, memory.snapshot().failures.values.single().reason)
        player.close()
    }

    @Test
    fun `an old player error cannot be renewed as a failure in the new environment`() = runTest {
        val memory = PlaybackSourceMemory()
        memory.updateEnvironment(NetworkEnvironment("wifi", true))
        val observer = PlaybackSourceObserver(memory, testScheduler.timeSource)
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        player.setMediaData(UriMediaData("https://site/video"), playWhenReady = true)
        val attempt = memory.beginAttempt(1, media)
        val job = backgroundScope.launch { observer.observe(player, attempt) }
        runCurrent()

        // 环境已失效，但旧播放器的错误事件与环境通知仍可能在同一调度轮里到达。
        memory.updateEnvironment(NetworkEnvironment("mobile", true))
        player.injectError(PlaybackException(PlaybackErrorCode.IO, "old attempt"))
        runCurrent()

        assertTrue(job.isCompleted)
        assertTrue(memory.snapshot().failures.isEmpty())
        player.close()
    }

    @Test
    fun `cancelled unsupported and common environment failures do not poison a route`() {
        val memory = PlaybackSourceMemory()
        val observer = PlaybackSourceObserver(memory)
        val attempt = memory.beginAttempt(1, media)
        listOf(
            VideoLoadingState.Cancelled, VideoLoadingState.NetworkError,
            VideoLoadingState.WebEngineUnavailable, VideoLoadingState.UnsupportedMedia,
            VideoLoadingState.UnknownError(PlaybackException(PlaybackErrorCode.DECODING, "test")),
        ).forEach { observer.onLoadFailed(attempt, it) }
        assertTrue(memory.snapshot().failures.isEmpty())
        memory.updateEnvironment(NetworkEnvironment(null, false))
        observer.onLoadFailed(attempt, VideoLoadingState.ResolutionTimedOut)
        assertTrue(memory.snapshot().failures.isEmpty())
        memory.updateEnvironment(NetworkEnvironment("wifi", true))
        // 默认网络已验证连通时，单个站点的网络失败可以作为这条线路的临时失败证据。
        observer.onLoadFailed(attempt, VideoLoadingState.NetworkError)
        assertEquals(1, memory.snapshot().failures.size)
    }
}
