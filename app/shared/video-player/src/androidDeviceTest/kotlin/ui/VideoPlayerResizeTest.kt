/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import android.graphics.Rect
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.children
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.source.UriMediaData
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class VideoPlayerResizeTest {
    @Test
    fun playingSurfaceFollowsFullscreenWidth() = checkResize(paused = false)

    @Test
    fun pausedEffectBufferSurvivesFullscreenResize() = checkResize(paused = true)

    @Test
    fun endedEffectBufferSurvivesFullscreenResize() = checkResize(paused = true, ended = true)

    private fun checkResize(paused: Boolean, ended: Boolean = false) = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        lateinit var composeView: View
        lateinit var player: ExoPlayerMediampPlayer
        var viewport = Rect()
        val renderedFrames = AtomicInteger()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            player = remember {
                ExoPlayerMediampPlayer(context, scope.coroutineContext).apply {
                    if (paused) impl.setVideoEffects(emptyList())
                    impl.repeatMode = if (ended) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    impl.setVideoFrameMetadataListener { _, _, _, _ -> renderedFrames.incrementAndGet() }
                }
            }
            composeView = LocalView.current
            DisposableEffect(player) { onDispose { player.close() } }
            LaunchedEffect(player) {
                val assetName = "resize-test.mp4"
                val video = File(context.cacheDir, assetName)
                InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
                    video.outputStream().use { input.copyTo(it) }
                }
                player.setMediaData(UriMediaData(video.toURI().toString()), playWhenReady = true)
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth(if (fullscreen) 1f else 0.7f)
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val bounds = it.boundsInWindow()
                            viewport = Rect(
                                bounds.left.roundToInt(), bounds.top.roundToInt(),
                                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                            )
                        },
                ) {
                    VideoPlayer(player, Modifier.fillMaxSize())
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) { player.currentPositionMillis.value > 0 && renderedFrames.get() >= 2 }
        if (ended) {
            waitUntil(timeoutMillis = 10_000) { runOnIdle { player.impl.playbackState == Player.STATE_ENDED } }
        }
        if (paused) {
            runOnIdle { player.pause() }
        }
        val surface = runOnIdle { composeView.surfaceViews().single() }
        val pausedBuffer = runOnIdle { Rect(surface.holder.surfaceFrame) }

        fun assertVideoBounds() {
            waitForIdle()
            runOnIdle {
                assertSame(surface, composeView.surfaceViews().single(), "Resize preserves the video output")
                val location = IntArray(2)
                surface.getLocationInWindow(location)
                val scale = minOf(viewport.width() / 320f, viewport.height() / 180f)
                assertTrue(abs(surface.width - 320 * scale) <= 2, "Surface width fits viewport: $viewport, ${surface.width}")
                assertTrue(abs(surface.height - 180 * scale) <= 2, "Surface height fits viewport: $viewport, ${surface.height}")
                assertTrue(abs(location[0] + surface.width / 2f - viewport.exactCenterX()) <= 2, "Video is horizontally centered")
                assertTrue(abs(location[1] + surface.height / 2f - viewport.exactCenterY()) <= 2, "Video is vertically centered")
            }
        }
        assertVideoBounds()
        repeat(3) {
            val positionBeforeResize = runOnIdle { player.impl.currentPosition }
            runOnIdle { fullscreen = true }
            assertVideoBounds()
            if (paused) {
                runOnIdle {
                    assertEquals(pausedBuffer, surface.holder.surfaceFrame, "The paused buffer retains its complete frame")
                    assertEquals(false, player.impl.playWhenReady)
                    assertEquals(positionBeforeResize, player.impl.currentPosition)
                }
            }
            runOnIdle { fullscreen = false }
            assertVideoBounds()
            if (paused) {
                runOnIdle { assertEquals(pausedBuffer, surface.holder.surfaceFrame) }
            }
        }
        if (paused) {
            val framesBeforeResume = renderedFrames.get()
            runOnIdle {
                fullscreen = true
                player.play()
            }
            waitUntil("playback resumes into the resized output", timeoutMillis = 5_000) {
                renderedFrames.get() > framesBeforeResume && runOnIdle {
                    surface.holder.surfaceFrame.width() == surface.width &&
                        surface.holder.surfaceFrame.height() == surface.height
                }
            }
            assertVideoBounds()
        }
    }

    private fun View.surfaceViews(): Sequence<SurfaceView> = sequence {
        if (this@surfaceViews is SurfaceView) yield(this@surfaceViews)
        if (this@surfaceViews is ViewGroup) {
            for (child in children) yieldAll(child.surfaceViews())
        }
    }
}
