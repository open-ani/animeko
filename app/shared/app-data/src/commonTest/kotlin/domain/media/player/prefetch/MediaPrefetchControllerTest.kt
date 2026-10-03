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
import kotlin.test.assertTrue

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

    private fun request(start: Long, end: Long, bufferedUntil: Long = 0) =
        MediaPrefetchRequest(MediaTimeRange(start, end), requireBufferedUntilMillis = bufferedUntil)

    @Test
    fun `forwards range to hls session for uri media once buffered`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val sessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(null)
        val controller = MediaPrefetchController(player, sessionFlow, backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/playlist.m3u8"))
        sessionFlow.value = session
        player.injectBufferedPosition(60_000)
        runCurrent()

        controller.setPrefetchRequest(request(90_000, 120_000, bufferedUntil = 37_000))
        runCurrent()
        assertEquals(MediaTimeRange(90_000, 120_000), session.ranges.last())

        controller.setPrefetchRequest(null)
        runCurrent()
        assertEquals(null, session.ranges.last())
    }

    @Test
    fun `does not start until content before the chapter is buffered`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val controller = MediaPrefetchController(player, MutableStateFlow<HlsPlaybackProxySession?>(session), backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/playlist.m3u8"))
        runCurrent()

        // 慢网: 只缓冲到 20s, 而 OP 在 37s 开始. 此时预缓存会和正片抢带宽, 不能启动
        player.injectBufferedPosition(20_000)
        controller.setPrefetchRequest(request(122_000, 152_000, bufferedUntil = 37_000))
        runCurrent()
        assertTrue(session.ranges.all { it == null }, "prefetch must not start: ${session.ranges}")

        player.injectBufferedPosition(30_000)
        runCurrent()
        assertTrue(session.ranges.all { it == null }, "prefetch must not start: ${session.ranges}")

        // 缓冲越过 OP 开头 (允许 1 秒误差): 之后播放器下载的都是要被跳过的内容, 可以启动
        player.injectBufferedPosition(36_500)
        runCurrent()
        assertEquals(MediaTimeRange(122_000, 152_000), session.ranges.last())
    }

    @Test
    fun `stays started when the buffered position drops afterwards`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val controller = MediaPrefetchController(player, MutableStateFlow<HlsPlaybackProxySession?>(session), backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/playlist.m3u8"))
        player.injectBufferedPosition(40_000)
        runCurrent()
        controller.setPrefetchRequest(request(122_000, 152_000, bufferedUntil = 37_000))
        runCurrent()
        assertEquals(MediaTimeRange(122_000, 152_000), session.ranges.last())

        val callsBefore = session.ranges.size
        player.injectBufferedPosition(10_000)
        runCurrent()
        assertEquals(callsBefore, session.ranges.size, "must not cancel or re-issue: ${session.ranges}")
    }

    @Test
    fun `unknown buffered position keeps prefetch off`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val controller = MediaPrefetchController(player, MutableStateFlow<HlsPlaybackProxySession?>(session), backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/playlist.m3u8"))
        runCurrent()
        controller.setPrefetchRequest(request(122_000, 152_000, bufferedUntil = 37_000))
        runCurrent()
        assertTrue(session.ranges.all { it == null })
    }

    @Test
    fun `request is bound to the media it was made for`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val session = FakeHlsSession()
        val sessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(session)
        val controller = MediaPrefetchController(player, sessionFlow, backgroundScope)
        player.setMediaData(UriMediaData("http://127.0.0.1:1/a.m3u8"))
        player.injectBufferedPosition(5_000)
        runCurrent()
        controller.setPrefetchRequest(request(1_000, 2_000))
        runCurrent()
        assertEquals(MediaTimeRange(1_000, 2_000), session.ranges.last())

        // 切换媒体后, 旧请求不再作用于新媒体
        player.setMediaData(UriMediaData("http://127.0.0.1:1/b.m3u8"))
        player.injectBufferedPosition(5_000)
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
