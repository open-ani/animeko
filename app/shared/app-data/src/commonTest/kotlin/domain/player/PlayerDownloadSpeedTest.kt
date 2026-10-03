/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 在线数据源的速度会被周期采样, 采样 ticker 永不空闲, 所以打开 [UriMediaData] 后不能再用
 * [advanceUntilIdle], 改用 [runCurrent] 和 [advanceTimeBy] 推进虚拟时钟.
 */
@OptIn(ExperimentalMediampApi::class)
class PlayerDownloadSpeedTest {
    private fun TestScope.createPlayer(): TestMediampPlayer =
        TestMediampPlayer(StandardTestDispatcher(testScheduler))

    private val period = PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD

    @Test
    fun `no media is unspecified`() = runTest {
        val player = createPlayer()
        player.downloadSpeedFlow().test {
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `uri media without a known speed is unspecified`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())
            advanceTimeBy(period * 3)
            expectNoEvents()
        }
    }

    @Test
    fun `uri media follows the network stats of the player`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())

            player.injectDownloadSpeed(1_500_000L)
            advanceTimeBy(period)
            assertEquals(1_500_000L.bytes, awaitItem())

            player.injectDownloadSpeed(0L)
            advanceTimeBy(period)
            assertEquals(FileSize.Zero, awaitItem())

            player.injectDownloadSpeed(NetworkStats.UNKNOWN_SPEED)
            advanceTimeBy(period)
            assertEquals(FileSize.Unspecified, awaitItem())
        }
    }

    @Test
    fun `uri media speed is sampled once per period`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())

            // 一个周期内的多次更新只保留最后一个, 且不会提前发出.
            player.injectDownloadSpeed(100L)
            runCurrent()
            player.injectDownloadSpeed(200L)
            advanceTimeBy(period / 2)
            player.injectDownloadSpeed(300L)
            expectNoEvents()

            advanceTimeBy(period / 2)
            assertEquals(300L.bytes, awaitItem())

            // 没有新值的周期不重复发出.
            advanceTimeBy(period * 3)
            expectNoEvents()
        }
    }

    @Test
    fun `speed becomes unspecified again when media is reopened`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/first.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())
            player.injectDownloadSpeed(1_000L)
            advanceTimeBy(period)
            assertEquals(1_000L.bytes, awaitItem())

            player.setMediaData(UriMediaData("https://example.com/second.m3u8"))
            advanceTimeBy(period)
            // 重新打开时播放器重置速度, 且 mediaData 切换, 两者都会产生 Unspecified.
            assertEquals(FileSize.Unspecified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
