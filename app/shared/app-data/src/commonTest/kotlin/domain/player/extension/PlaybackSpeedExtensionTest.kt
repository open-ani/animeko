/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSpeedExtensionTest : AbstractPlayerExtensionTest() {
    private val newEpisodeId = 3

    private fun TestScope.createCase(
        playbackSpeedFlow: MutableStateFlow<Float>,
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)

        val state = suite.createState(
            extensions = listOf(PlaybackSpeedExtension.Factory(playbackSpeedFlow)),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    private val EpisodePlayerTestSuite.playerSpeed: Float?
        get() = player.features[PlaybackSpeed]?.value

    /**
     * 播放页内切集时倍速必须保持. 播放器在换片源后可能把速度重置回 1x, 扩展需要在新 session 上重新应用.
     */
    @Test
    fun `reapplies the speed after switching episode`() = runTest {
        val speed = MutableStateFlow(1f)
        val (testScope, suite, state) = createCase(speed)
        try {
            speed.value = 1.75f
            advanceUntilIdle()
            assertEquals(1.75f, suite.playerSpeed)

            // 模拟播放器换片源后速度被重置
            suite.player.features[PlaybackSpeed]?.set(1f)
            state.switchEpisode(newEpisodeId)
            advanceUntilIdle()

            assertEquals(1.75f, suite.playerSpeed)
        } finally {
            testScope.cancel()
        }
    }

    /**
     * 播放器在媒体加载完成后可能把速度重置回 1x, 扩展需要在媒体就绪时重新应用.
     */
    @Test
    fun `reapplies the speed when media becomes loaded`() = runTest {
        val speed = MutableStateFlow(1.75f)
        val (testScope, suite, _) = createCase(speed)
        try {
            advanceUntilIdle()
            assertEquals(1.75f, suite.playerSpeed)

            // 模拟新媒体加载过程中底层重置倍速为 1x
            suite.player.loadMedia(100_000L)
            suite.player.features[PlaybackSpeed]?.set(1f)
            suite.setMediaDuration(100_000L)
            advanceUntilIdle()

            assertEquals(1.75f, suite.playerSpeed)
        } finally {
            testScope.cancel()
        }
    }
}
