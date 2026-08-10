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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.source.UriMediaData
import kotlin.system.exitProcess

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
                ?: System.getProperty("mediamp.mpv.dev.native.dir")
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
            player.setMediaData(UriMediaData(path), playWhenReady = true)
        }
        LaunchedEffect(Unit) {
            // 控制台进度输出, 便于无头验证状态机与位置推进
            while (true) {
                println("[verify] state=${player.state.value} pos=${player.currentPositionMillis.value}ms")
                kotlinx.coroutines.delay(1000)
            }
        }
        if (System.getProperty("ani.mpv.selftest") == "true") {
            LaunchedEffect(Unit) {
                runMpvSelfTest(player)
            }
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
                    val state by player.state.collectAsState()
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

/**
 * 无头自测: 通过真实 mpv 管线依次验证 pause 冻结 / play 恢复 / seek / 自然结束 (MediaEnded 事件) /
 * Ended 重播 / stopPlayback → Idle. 需要 -Dani.mpv.selftest=true, 视频时长应为 60s.
 * 结果打印 `[selftest] PASS` / `[selftest] FAIL: ...` 后退出进程.
 */
private suspend fun runMpvSelfTest(player: MpvMediampPlayer): Nothing {
    val events = mutableListOf<PlaybackEvent>()
    val eventScope = CoroutineScope(currentCoroutineContext())
    eventScope.launch { player.events.collect { events += it } }

    fun fail(message: String): Nothing {
        println("[selftest] FAIL: $message (state=${player.state.value} pos=${player.currentPositionMillis.value})")
        exitProcess(1)
    }
    suspend fun awaitStatus(name: String, timeoutMillis: Long, predicate: () -> Boolean) {
        try {
            withTimeout(timeoutMillis) {
                while (!predicate()) delay(50)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            fail("timeout awaiting $name")
        }
    }

    try {
        println("[selftest] start")
        player.state.first { it.mediaStatus == MediaStatus.Ready }
        println("[selftest] reached Ready")

        // 1. 播放推进
        delay(3000)
        val p1 = player.currentPositionMillis.value
        if (p1 < 1000) fail("position not advancing after 3s: $p1")
        println("[selftest] playback advancing: pos=$p1")

        // 2. pause 冻结
        player.pause()
        if (player.state.value.playWhenReady) fail("pause() did not clear playWhenReady synchronously")
        delay(700)
        val p2 = player.currentPositionMillis.value
        delay(1200)
        val p3 = player.currentPositionMillis.value
        if (p3 - p2 > 300) fail("position kept advancing while paused: $p2 -> $p3")
        println("[selftest] pause freezes position: $p2 -> $p3")

        // 3. play 恢复
        player.play()
        if (!player.state.value.playWhenReady) fail("play() did not set playWhenReady synchronously")
        delay(1500)
        val p4 = player.currentPositionMillis.value
        if (p4 <= p3 + 300) fail("position not advancing after play(): $p3 -> $p4")
        println("[selftest] play resumes: $p3 -> $p4")

        // 4. seek
        player.seekTo(30_000)
        awaitStatus("seek to land near 30s", 8000) {
            player.currentPositionMillis.value in 29_000..36_000
        }
        println("[selftest] seek landed: pos=${player.currentPositionMillis.value}")

        // 5. 自然结束 → MediaEnded 事件 + Ended 状态
        player.seekTo(57_000)
        awaitStatus("natural end (Ended status)", 20_000) {
            player.state.value.mediaStatus == MediaStatus.Ended
        }
        if (player.state.value.playWhenReady) fail("Ended must reset playWhenReady")
        if (events.none { it is PlaybackEvent.MediaEnded }) fail("no MediaEnded event observed")
        val ended = events.filterIsInstance<PlaybackEvent.MediaEnded>().first()
        println("[selftest] natural end: MediaEnded(final=${ended.finalPositionMillis}, duration=${ended.durationMillis})")

        // 6. Ended 重播
        player.play()
        awaitStatus("replay from start", 8000) {
            player.state.value.isPlaying && player.currentPositionMillis.value in 0..10_000
        }
        println("[selftest] replay from start: pos=${player.currentPositionMillis.value}")

        // 7. stopPlayback → Idle
        player.stopPlayback()
        awaitStatus("Idle after stopPlayback", 5000) {
            player.state.value.mediaStatus == MediaStatus.Idle
        }
        if (player.mediaData.value != null) fail("mediaData not cleared after stopPlayback")
        println("[selftest] stopPlayback -> Idle, mediaData cleared")

        println("[selftest] PASS")
        exitProcess(0)
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        fail("unexpected exception: $e")
    }
}
