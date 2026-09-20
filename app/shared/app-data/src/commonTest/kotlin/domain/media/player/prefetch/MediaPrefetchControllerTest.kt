/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.player.ChunkState
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 注意: 控制器在 `backgroundScope` 中收集, 而 `advanceUntilIdle` 只在有前台任务时才顺带执行后台任务,
 * 因此这里用 `runCurrent` 推进.
 */
class MediaPrefetchControllerTest {
    private class FakeHlsSession : HlsPlaybackProxySession {
        val ranges = mutableListOf<MediaTimeRange?>()
        override val prefetchProgress = MutableStateFlow<List<PrefetchSegmentInfo>>(emptyList())
        override fun setPrefetchRange(range: MediaTimeRange?) {
            ranges += range
        }

        override fun close() {}
    }

    @Test
    fun `forwards range to hls session for uri media`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val sessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(null)
        val controller = MediaPrefetchController(player, sessionFlow, backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/playlist.m3u8"))
        sessionFlow.value = session
        runCurrent()

        controller.setPrefetchRange(MediaTimeRange(90_000, 120_000))
        runCurrent()
        assertEquals(MediaTimeRange(90_000, 120_000), session.ranges.last())

        controller.setPrefetchRange(null)
        runCurrent()
        assertEquals(null, session.ranges.last())
    }

    @Test
    fun `request is bound to the media it was made for`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val sessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(session)
        val controller = MediaPrefetchController(player, sessionFlow, backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/a.m3u8"))
        runCurrent()
        controller.setPrefetchRange(MediaTimeRange(1_000, 2_000))
        runCurrent()
        assertEquals(MediaTimeRange(1_000, 2_000), session.ranges.last())

        // 切换媒体后, 旧请求不再作用于新媒体
        player.setMediaData(UriMediaData("http://127.0.0.1:1/b.m3u8"))
        runCurrent()
        assertEquals(null, session.ranges.last())
    }

    @Test
    fun `prefetch progress follows the active hls session`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val sessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(null)
        val controller = MediaPrefetchController(player, sessionFlow, backgroundScope)
        val progress: Flow<List<PrefetchSegmentInfo>> = controller.prefetchProgress
        progress.test {
            assertEquals(emptyList(), awaitItem())
            sessionFlow.value = session
            assertEquals(emptyList(), awaitItem())
            val segments = listOf(PrefetchSegmentInfo(MediaTimeRange(0, 10_000), ChunkState.DOWNLOADING))
            session.prefetchProgress.value = segments
            assertEquals(segments, awaitItem())
            sessionFlow.value = null
            assertEquals(emptyList(), awaitItem())
        }
    }
}
