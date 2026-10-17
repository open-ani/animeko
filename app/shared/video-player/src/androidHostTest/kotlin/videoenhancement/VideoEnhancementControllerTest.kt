/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package me.him188.ani.app.videoplayer.videoenhancement

import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VideoEnhancementControllerTest {
    @Test
    fun onlyModeChangesReplaceEffects() = runTest {
        val player = TestMediampPlayer(backgroundScope.coroutineContext)
        val effects = mutableListOf<List<Effect>>()
        val controller = ExoPlayerVideoEnhancementController(
            player, { effects.add(it) }, flowOf(false), backgroundScope.coroutineContext,
        )
        try {
            controller.setViewportSize(1920, 1080)
            controller.setMode(VideoEnhancementMode.QUALITY)
            runCurrent()
            assertEquals(1, effects.size)
            assertEquals(3, effects.single().size)
            val scaler = assertIs<DesktopStyleLanczosSharpEffect>(effects.single().last())
            assertEquals(VideoDimensions(1920, 1080), scaler.viewportSize)

            player.setMediaData(UriMediaData("file:///test.mp4"))
            runCurrent()
            player.injectProperties(MediaProperties(videoWidth = 1280, videoHeight = 720))
            runCurrent()
            player.seekTo(20_000)
            runCurrent()
            player.injectProperties(MediaProperties())
            runCurrent()
            player.injectProperties(MediaProperties(videoWidth = 1280, videoHeight = 720))
            runCurrent()
            assertEquals(1, effects.size, "Metadata loss and recovery must retain compiled shaders")

            // A fullscreen switch reports intermediate layout sizes in quick succession.
            controller.setViewportSize(60, 33)
            runCurrent()
            controller.setViewportSize(2560, 1440)
            runCurrent()
            assertEquals(1, effects.size, "A viewport resize must not replace the effect list")
            assertEquals(VideoDimensions(2560, 1440), scaler.viewportSize)

            controller.setMode(VideoEnhancementMode.PERFORMANCE)
            runCurrent()
            assertEquals(2, effects.size)
            val performanceScaler = assertIs<DesktopStyleLanczosSharpEffect>(effects.last().last())
            assertEquals(VideoDimensions(2560, 1440), performanceScaler.viewportSize)

            controller.setMode(VideoEnhancementMode.OFF)
            runCurrent()
            assertEquals(emptyList(), effects.last())
        } finally {
            controller.close()
            player.close()
        }
    }

    @Test
    fun scalerOutputFitsViewport() {
        assertEquals(Size(1920, 1080), lanczosSharpOutputSize(1280, 720, VideoDimensions(1920, 1080)))
        assertEquals(Size(1920, 1080), lanczosSharpOutputSize(1280, 720, VideoDimensions(2400, 1080)))
        assertEquals(Size(1080, 608), lanczosSharpOutputSize(1920, 1080, VideoDimensions(1080, 608)))
        assertEquals(Size(1280, 720), lanczosSharpOutputSize(1280, 720, viewport = null))
    }
}
