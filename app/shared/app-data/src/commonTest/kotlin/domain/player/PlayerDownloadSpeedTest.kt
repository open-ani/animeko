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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMediampApi::class)
class PlayerDownloadSpeedTest {
    private fun TestScope.createPlayer(): TestMediampPlayer =
        TestMediampPlayer(StandardTestDispatcher(testScheduler))

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
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `uri media follows the network stats of the player`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())

            player.injectDownloadSpeed(1_500_000L)
            advanceUntilIdle()
            assertEquals(1_500_000L.bytes, awaitItem())

            player.injectDownloadSpeed(0L)
            advanceUntilIdle()
            assertEquals(FileSize.Zero, awaitItem())

            player.injectDownloadSpeed(NetworkStats.UNKNOWN_SPEED)
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())
        }
    }

    @Test
    fun `speed becomes unspecified again when media is reopened`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/first.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())
            player.injectDownloadSpeed(1_000L)
            advanceUntilIdle()
            assertEquals(1_000L.bytes, awaitItem())

            player.setMediaData(UriMediaData("https://example.com/second.m3u8"))
            advanceUntilIdle()
            // 重新打开时播放器重置速度, 且 mediaData 切换, 两者都会产生 Unspecified.
            assertEquals(FileSize.Unspecified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
