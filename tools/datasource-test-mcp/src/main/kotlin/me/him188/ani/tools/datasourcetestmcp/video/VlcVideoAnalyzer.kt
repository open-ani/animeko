/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.Text
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openani.mediamp.InternalMediampApi
import java.io.File
import java.util.concurrent.Executors
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.vlc.VlcMediampPlayer
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurface
import java.awt.Dimension
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

/**
 * 视频能力: 用与 Animeko 桌面端完全相同的播放器 (mediamp-vlc, 即 VLC) 真实播放视频,
 * 验证可播放性并读取真实媒体信息 (分辨率/时长/编码/帧率/码率).
 *
 * 默认会弹出一个 Compose 测试窗口实时显示播放画面 (showWindow=false 可关).
 * 需要系统安装 VLC 3.0.18 (macOS: /Applications/VLC.app); 未安装时返回 `available = false`.
 */
class VlcVideoAnalyzer {
    class Output(
        val analysis: MediaAnalysisResult,
        val frames: List<CapturedFrame>,
    )

    suspend fun analyze(input: ProbeVideoInput): Output {
        runCatching { VlcMediampPlayer.prepareLibraries() }
            .onFailure { exception ->
                return Output(
                    MediaAnalysisResult(
                        available = false,
                        errors = listOf(
                            "VLC 原生库加载失败: ${exception.message.orEmpty()}. " +
                                    "请安装 VLC 3.0.18 (https://www.videolan.org/), macOS 放在 /Applications/VLC.app",
                        ),
                    ),
                    emptyList(),
                )
            }
        // 播放器要求单线程调用
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "vlc-video-analyzer").apply { isDaemon = true }
        }
        return try {
            withContext(executor.asCoroutineDispatcher()) {
                analyzeWithPlayer(input)
            }
        } finally {
            executor.shutdown()
        }
    }

    private suspend fun analyzeWithPlayer(input: ProbeVideoInput): Output {
        val errors = mutableListOf<String>()
        val frames = mutableListOf<CapturedFrame>()
        val player = VlcMediampPlayer(coroutineContext)
        val window = if (input.showWindow) createWindow(player, input.videoUrl) else null
        return try {
            val playbackTest = runPlaybackTest(player, input, errors, frames)

            // track 信息要等 demux 完成才可用, 轮询等待
            val info = withTimeoutOrNull(10_000) {
                while (player.player.media().info()?.videoTracks().isNullOrEmpty()) {
                    kotlinx.coroutines.delay(200)
                }
                player.player.media().info()
            } ?: player.player.media().info()

            val videoTrack = info?.videoTracks()?.firstOrNull()
            val audioTrack = info?.audioTracks()?.firstOrNull()
            val statistics = runCatching { info?.statistics() }.getOrNull()

            val durationMillis = player.getCurrentMediaProperties()?.durationMillis?.takeIf { it > 0 }
                ?: info?.duration()?.takeIf { it > 0 }

            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "animeko-player (vlc)",
                    durationSeconds = durationMillis?.let { it / 1000.0 },
                    overallBitrate = statistics?.let { overallBitsPerSecond(it.inputBitrate(), it.demuxBitrate()) },
                    video = videoTrack?.let { track ->
                        VideoStreamInfo(
                            codec = track.codecName()?.lowercase(),
                            width = track.width().takeIf { it > 0 },
                            height = track.height().takeIf { it > 0 },
                            frameRate = formatFrameRate(track.frameRate(), track.frameRateBase()),
                            bitrate = track.bitRate().takeIf { it > 0 }?.toLong(),
                        )
                    },
                    audio = audioTrack?.let { track ->
                        AudioStreamInfo(
                            codec = track.codecName()?.lowercase(),
                            sampleRate = track.rate().takeIf { it > 0 }?.toString(),
                            channels = track.channels().takeIf { it > 0 },
                            bitrate = track.bitRate().takeIf { it > 0 }?.toLong(),
                        )
                    },
                    playback = playbackTest,
                    errors = errors,
                ),
                frames,
            )
        } catch (e: Exception) {
            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "animeko-player (vlc)",
                    errors = errors + "${e::class.simpleName}: ${e.message.orEmpty()}",
                ),
                frames,
            )
        } finally {
            runCatching { player.close() }
            window?.let { w -> SwingUtilities.invokeLater { w.dispose() } }
        }
    }

    /**
     * 真实播放 [ProbeVideoInput.playSeconds] 秒, 并测量起播/首帧/缓冲耗时:
     * 打开媒体 → 等进入 PLAYING (起播) → 等位置首次前进 (首帧) → 等位置到达目标秒数 (播放测试).
     */
    private suspend fun runPlaybackTest(
        player: VlcMediampPlayer,
        input: ProbeVideoInput,
        errors: MutableList<String>,
        frames: MutableList<CapturedFrame>,
    ): PlaybackTestResult {
        val openStart = System.currentTimeMillis()
        try {
            player.setMediaData(UriMediaData(input.videoUrl, input.headers, MediaExtraFiles()))
        } catch (e: Exception) {
            errors += "打开媒体失败: ${e::class.simpleName}: ${e.message.orEmpty()}"
            return PlaybackTestResult(
                ran = true,
                ok = false,
                requestedSeconds = input.playSeconds,
                finalState = player.getCurrentPlaybackState().toString(),
                errors = errors.toList(),
                openMillis = System.currentTimeMillis() - openStart,
            )
        }
        val openMillis = System.currentTimeMillis() - openStart

        val resumeAt = System.currentTimeMillis()
        player.resume()

        val state = withTimeoutOrNull(input.playTimeoutMillis) {
            player.playbackState.first {
                it == PlaybackState.PLAYING || it == PlaybackState.ERROR || it == PlaybackState.FINISHED
            }
        }
        if (state != PlaybackState.PLAYING) {
            errors += when (state) {
                null -> "等待进入播放状态超时 (${input.playTimeoutMillis}ms)"
                else -> "未能进入播放状态: $state"
            }
            return PlaybackTestResult(
                ran = true,
                ok = false,
                requestedSeconds = input.playSeconds,
                playedPositionMillis = player.getCurrentPositionMillis(),
                finalState = (state ?: player.getCurrentPlaybackState()).toString(),
                errors = errors.toList(),
                openMillis = openMillis,
            )
        }
        val timeToPlayingMillis = System.currentTimeMillis() - resumeAt

        val timeToFirstFrameMillis = withTimeoutOrNull(input.playTimeoutMillis) {
            player.currentPositionMillis.first { it > 0 }
            System.currentTimeMillis() - resumeAt
        }

        // 首帧截图 (前贴片广告时此帧即广告画面)
        val framesDir = input.captureFramesDir?.let { File(it).apply { mkdirs() } }
        if (framesDir != null) {
            captureFrame(player, framesDir, "first_frame")?.let { frames += it }
        }

        // 播放期间的卡顿统计: 进入 PAUSED_BUFFERING 的次数与总时长
        var bufferingCount = 0
        var bufferingTotalMillis = 0L
        val targetPositionMillis = input.playSeconds.coerceIn(1, 60) * 1000L
        val playStart = System.currentTimeMillis()
        val reached = coroutineScope {
            val bufferingTracker = launch {
                var enteredAt = 0L
                player.playbackState.collect { st ->
                    val now = System.currentTimeMillis()
                    if (st == PlaybackState.PAUSED_BUFFERING) {
                        bufferingCount++
                        enteredAt = now
                    } else if (enteredAt != 0L) {
                        bufferingTotalMillis += now - enteredAt
                        enteredAt = 0L
                    }
                }
            }
            // 多时间点采样: 播放位置越过每个采样秒时截一帧
            val sampler = if (framesDir != null && input.captureAtSeconds.isNotEmpty()) {
                launch {
                    val remaining = input.captureAtSeconds.filter { it >= 0 }.sorted().toMutableList()
                    player.currentPositionMillis.collect { pos ->
                        while (remaining.isNotEmpty() && pos >= remaining.first() * 1000L) {
                            val sec = remaining.removeAt(0)
                            captureFrame(player, framesDir, "frame_%02ds".format(sec))?.let { frames += it }
                        }
                        if (remaining.isEmpty()) return@collect
                    }
                }
            } else {
                null
            }
            try {
                withTimeoutOrNull(input.playTimeoutMillis) {
                    merge(
                        player.currentPositionMillis.filter { it >= targetPositionMillis }.map { true },
                        player.playbackState.filter { it == PlaybackState.ERROR }.map { false },
                    ).first()
                }
            } finally {
                bufferingTracker.cancel()
                sampler?.cancel()
            }
        }
        val playWallClockMillis = System.currentTimeMillis() - playStart

        // 播满目标秒数后再截一帧 (此时通常已是正片, 与首帧对比可判断前贴片广告)
        if (framesDir != null && reached == true) {
            captureFrame(player, framesDir, "mid")?.let { frames += it }
        }

        if (reached != true) {
            errors += when (reached) {
                false -> "播放中途出错 (state=ERROR)"
                else -> "播放 ${input.playSeconds}s 超时: 位置停在 ${player.getCurrentPositionMillis()}ms"
            }
        }
        return PlaybackTestResult(
            ran = true,
            ok = reached == true,
            requestedSeconds = input.playSeconds,
            playedPositionMillis = player.getCurrentPositionMillis(),
            finalState = player.getCurrentPlaybackState().toString(),
            errors = if (reached == true) emptyList() else errors.toList(),
            openMillis = openMillis,
            timeToPlayingMillis = timeToPlayingMillis,
            timeToFirstFrameMillis = timeToFirstFrameMillis,
            playWallClockMillis = playWallClockMillis,
            bufferingCount = bufferingCount,
            bufferingTotalMillis = bufferingTotalMillis,
        )
    }

    /**
     * 从播放器渲染表面 (SkiaBitmapVideoSurface) 抓取当前帧存为 PNG.
     * bitmap 仅在 PLAYING 时更新, 首次可能尚未就绪, 短暂重试.
     */
    @OptIn(InternalMediampApi::class)
    private suspend fun captureFrame(
        player: VlcMediampPlayer,
        dir: File,
        label: String,
    ): CapturedFrame? {
        repeat(25) {
            val bitmap = player.surface.bitmap
            if (bitmap != null) {
                return runCatching {
                    val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
                    val data = image.encodeToData(EncodedImageFormat.PNG)
                        ?: return@runCatching null
                    val file = dir.resolve("$label.png")
                    file.writeBytes(data.bytes)
                    CapturedFrame(
                        positionMillis = player.getCurrentPositionMillis(),
                        path = file.absolutePath,
                        label = label,
                    )
                }.getOrNull()
            }
            delay(200)
        }
        return null
    }

    private fun createWindow(player: VlcMediampPlayer, videoUrl: String): ComposeWindow {
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Animeko 数据源测试 - probe_video"
                size = Dimension(960, 600)
                setLocationRelativeTo(null)
                setContent { ProbeWindowContent(player, videoUrl) }
                isVisible = true
            }
        }
        return window
    }

    /**
     * libvlc 统计的码率单位约定为 bytes/ms, *8000 得 kbps.
     */
    private fun overallBitsPerSecond(inputBitrate: Float, demuxBitrate: Float): Long? {
        val rate = demuxBitrate.takeIf { it > 0f } ?: inputBitrate.takeIf { it > 0f } ?: return null
        return (rate * 8_000_000).toLong().takeIf { it in 1_000..2_000_000_000 }
    }

    private fun formatFrameRate(frameRate: Int, frameRateBase: Int): String? {
        if (frameRate <= 0 || frameRateBase <= 0) return null
        return "%.3f".format(frameRate.toDouble() / frameRateBase).trimEnd('0').trimEnd('.')
    }
}

@Composable
private fun ProbeWindowContent(player: VlcMediampPlayer, videoUrl: String) {
    val state by player.playbackState.collectAsState()
    val properties by player.mediaProperties.collectAsState()
    val position by player.currentPositionMillis.collectAsState()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VlcMediampPlayerSurface(player, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp)) {
            Text(
                "probe_video: 用 Animeko 播放器 (VLC) 真实播放测试",
                color = Color.White,
                fontSize = 13.sp,
            )
            Text(
                videoUrl,
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "state=$state  position=${position / 1000.0}s  duration=${(properties?.durationMillis ?: 0) / 1000.0}s",
                color = Color(0xFF80CBC4),
                fontSize = 12.sp,
            )
        }
    }
}
