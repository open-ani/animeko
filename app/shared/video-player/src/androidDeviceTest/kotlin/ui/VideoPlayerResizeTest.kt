/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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

/**
 * Resizes [VideoPlayer] the way entering and leaving fullscreen does, with the video effects
 * pipeline that `PlayerKernelConfig.exoPlayerInitEffectGraphInAdvance` sets up by default.
 *
 * `resize-test.mp4` is a 4-second 320 × 180 H.264 color-bar video:
 *
 * ```shell
 * ffmpeg -f lavfi -i 'smptebars=size=320x180:rate=10' -t 4 \
 *   -c:v libx264 -pix_fmt yuv420p -movflags +faststart resize-test.mp4
 * ```
 */
@OptIn(UnstableApi::class)
class VideoPlayerResizeTest {
    @Test
    fun playingVideoFollowsFullscreenWidth() = checkResize(StillFrame.NONE)

    @Test
    fun pausedFrameSurvivesFullscreenResize() = checkResize(StillFrame.PAUSED)

    @Test
    fun endedFrameSurvivesFullscreenResize() = checkResize(StillFrame.ENDED)

    @Test
    fun firstFrameBeforePlaybackSurvivesFullscreenResize() = checkResize(StillFrame.NOT_STARTED)

    /**
     * What keeps the frame on screen still while the player resizes.
     */
    private enum class StillFrame {
        /** Playback runs during the resize. */
        NONE,
        PAUSED,
        ENDED,

        /** The media opens paused and shows its first frame. */
        NOT_STARTED,
    }

    private fun checkResize(still: StillFrame) = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        lateinit var composeView: View
        lateinit var player: ExoPlayerMediampPlayer
        var viewport = Rect()
        val renderedFrames = AtomicInteger()
        val reportedFirstFrames = AtomicInteger()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            player = remember {
                ExoPlayerMediampPlayer(context, scope.coroutineContext).apply {
                    impl.setVideoEffects(emptyList())
                    impl.repeatMode = if (still == StillFrame.ENDED) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    impl.setVideoFrameMetadataListener { _, _, _, _ -> renderedFrames.incrementAndGet() }
                    impl.addListener(
                        object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                reportedFirstFrames.incrementAndGet()
                            }
                        },
                    )
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
                player.setMediaData(
                    UriMediaData(video.toURI().toString()),
                    playWhenReady = still != StillFrame.NOT_STARTED,
                )
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
        if (still == StillFrame.NOT_STARTED) {
            waitUntil("the first frame is rendered", timeoutMillis = 15_000) { reportedFirstFrames.get() > 0 }
        } else {
            waitUntil("playback starts", timeoutMillis = 15_000) {
                player.currentPositionMillis.value > 0 && renderedFrames.get() >= 2
            }
        }
        if (still == StillFrame.ENDED) {
            waitUntil("playback ends", timeoutMillis = 10_000) {
                runOnIdle { player.impl.playbackState == Player.STATE_ENDED }
            }
        }
        if (still == StillFrame.PAUSED) {
            runOnIdle { player.pause() }
        }
        waitForIdle()
        val surface = runOnIdle { composeView.surfaceViews().single() }
        val stillBuffer = runOnIdle { Rect(surface.holder.surfaceFrame) }
        if (still != StillFrame.NONE) {
            assertTrue(
                abs(stillBuffer.width() * 180f / (stillBuffer.height() * 320f) - 1) <= 0.02f,
                "The still frame was rendered for the video's shape: $stillBuffer",
            )
        }

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

        fun assertBufferMatchesState() {
            if (still == StillFrame.NONE) {
                waitUntil("the buffer follows the layout while playing", timeoutMillis = 5_000) {
                    runOnIdle { surface.holder.surfaceFrame.width() == surface.width }
                }
            } else {
                runOnIdle {
                    assertEquals(stillBuffer, surface.holder.surfaceFrame, "The still frame keeps its buffer")
                }
            }
        }

        assertVideoBounds()
        repeat(3) {
            val positionBeforeResize = runOnIdle { player.impl.currentPosition }
            runOnIdle { fullscreen = true }
            assertVideoBounds()
            assertBufferMatchesState()
            if (still != StillFrame.NONE) {
                runOnIdle {
                    assertEquals(false, player.impl.playWhenReady)
                    assertEquals(positionBeforeResize, player.impl.currentPosition)
                }
            }
            runOnIdle { fullscreen = false }
            assertVideoBounds()
            assertBufferMatchesState()
        }
        // Playing again after the end seeks to the start. On emulators, media3's effects pipeline
        // can hold a seek for up to 20 seconds waiting for decoder frames that never arrive
        // (ExternalTextureManager.SURFACE_TEXTURE_TIMEOUT_MS), so the ended case stops here.
        if (still == StillFrame.PAUSED || still == StillFrame.NOT_STARTED) {
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
