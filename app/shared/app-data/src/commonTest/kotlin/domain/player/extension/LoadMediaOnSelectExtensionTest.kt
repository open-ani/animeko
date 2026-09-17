/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.player.data.AniSystemFileMediaData
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.writeBytes
import org.openani.mediamp.source.UriMediaData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * @see EpisodeFetchSelectPlayState.LoadMediaOnSelectExtension
 */
class LoadMediaOnSelectExtensionTest : AbstractPlayerExtensionTest() {
    private fun TestScope.createCase(
        mediaResolver: MediaResolver = TestUniversalMediaResolver,
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
        suite.registerComponent<MediaResolver> {
            mediaResolver
        }

        val state = suite.createState(listOf()) // LoadMediaOnSelectExtension is intrinsic
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `can load media on select`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        val myMedia = TestMediaList[0]
        ms1.complete(listOf(myMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
        advanceUntilIdle()

        assertIs<UriMediaData>(suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }

    @Test
    fun `plays a dropped file while media sources are still fetching`() = runTest {
        val (testScope, suite, state) =
            createCase(LocalFileMediaResolver())

        suite.mediaSelectorTestBuilder.delayedMediaSource("1") // 一直未完成查询

        // 播放器会打开文件, 因此需要一个真实存在的文件
        val file = Path(SystemTemporaryDirectory, "ani-dropped-${Random.nextLong()}.mkv").inSystem
        file.writeBytes(byteArrayOf(0))
        try {
            state.mediaSelectorFlow.filterNotNull().first().selectTemporarily(DroppedFileMedia.create(file))
            advanceUntilIdle()

            assertIs<VideoLoadingState.Succeed>(state.playerSession.videoLoadingState.value)
            val data = assertIs<AniSystemFileMediaData>(suite.player.mediaData.first())
            assertEquals(file.name, data.filename)
        } finally {
            testScope.cancel()
            file.delete()
        }
    }

    @Test
    fun `can load media and reset player on select`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        // v2: give the player prior playback state that the select must replace.
        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        val myMedia = TestMediaList[0]
        ms1.complete(listOf(myMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
        advanceUntilIdle()

        assertIs<UriMediaData>(suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }

    @Test
    fun `switch media resets player`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        // v2: give the player prior playback state that the select must replace.
        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        // Fetch complete
        ms1.complete(TestMediaList.take(2))

        // Select media        
        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle() // should reset player 
        val previousData = suite.player.mediaData.first()
        assertIs<UriMediaData>(previousData)
        assertEquals(0, suite.player.currentPositionMillis.value)


        // Let's play it for a while
        suite.player.seekTo(2000)


        // Switch media
        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[1])
        advanceUntilIdle()
        assertNotSame(previousData, suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value) // should reset

        testScope.cancel()
    }

    @Test
    fun `noop when unselect`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        // v2: give the player prior playback state that the select must replace.
        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        // Fetch complete
        ms1.complete(TestMediaList.take(2))

        // Select media        
        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle() // should reset player 
        val previousData = suite.player.mediaData.first()
        assertIs<UriMediaData>(previousData)
        assertEquals(0, suite.player.currentPositionMillis.value)


        // Let's play it for a while
        suite.player.seekTo(2000)

        // Unselect media
        state.mediaSelectorFlow.filterNotNull().first().unselect()
        advanceUntilIdle() // State should not change
        assertSame(previousData, suite.player.mediaData.first())
        assertEquals(2000, suite.player.currentPositionMillis.value)


        testScope.cancel()
    }
}