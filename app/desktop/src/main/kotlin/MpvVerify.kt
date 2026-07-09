/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.him188.ani.app.videoplayer.ui.gesture.keyboardSeekAndFastForward
import me.him188.ani.app.videoplayer.ui.gesture.rememberSwipeSeekerState
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressSlider
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.source.UriMediaData

/**
 * Scratch verification app for the mpv backend (hwdec + Metal/IOSurface rendering):
 * plays a local file through the REAL player pipeline used by
 * EpisodeVideo (keyboardSeekAndFastForward -> SwipeSeekerState.onSeek -> MediampPlayer.skip,
 * real MediaProgressSlider mirroring currentPositionMillis).
 *
 * Run: ./gradlew :app:desktop:run -Pani.desktop.mainClass=me.him188.ani.app.desktop.MpvVerifyKt
 * Video path: -Dani.seekverify.video=... (default /tmp/seek-verify.mp4)
 * Native dir: -Dani.mpv.native.dir=... (injected by Gradle from ani.build.mediamp.path; falls back to the bundled runtime)
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(),
        title = "MpvVerify",
    ) {
        val scope = rememberCoroutineScope()
        val player = remember {
            val nativeDir = System.getProperty("ani.mpv.native.dir")
            if (nativeDir != null) {
                MpvMediampPlayer.prepareLibraries(nativeDir, extractRuntimeLibrary = false)
            } else {
                MpvMediampPlayer.prepareLibraries()
            }
            org.openani.mediamp.mpv.MPVHandle.setLogHandler { msg ->
                println("[mpv/${msg.prefix}] ${msg.line}")
            }
            MpvMediampPlayer(Any(), scope.coroutineContext)
        }
        LaunchedEffect(Unit) {
            val path = System.getProperty("ani.seekverify.video") ?: "/tmp/seek-verify.mp4"
            player.setMediaData(UriMediaData(path))
            player.resume()
        }

        val focusRequester = remember { FocusRequester() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val seeker = rememberSwipeSeekerState(constraints.maxWidth) {
                player.skip(it * 1000L) // same wiring as EpisodeVideo.kt
            }
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .keyboardSeekAndFastForward(
                        onSeekBackward = { seeker.onSeek(-5) },
                        onSeekForward = { seeker.onSeek(5) },
                        fastSkipState = null,
                    ),
            ) {
                MpvMediampPlayerSurface(player, Modifier.fillMaxSize())

                Column(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                ) {
                    val positionMillis by player.currentPositionMillis.collectAsState()
                    val state by player.playbackState.collectAsState()
                    Text(
                        "pos=$positionMillis ms  state=$state",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                    )
                    val sliderState = rememberMediaProgressSliderState(
                        player,
                        onPreview = {},
                        onPreviewFinished = { player.seekTo(it) },
                    )
                    MediaProgressSlider(sliderState, cacheProgressInfoFlow = { null })
                }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}
