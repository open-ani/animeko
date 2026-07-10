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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.Screenshots
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import java.awt.Dimension
import java.io.File
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

/**
 * 视频能力: 用与 Animeko 桌面端完全相同的播放器 (mediamp-mpv, hwdec=videotoolbox) 真实播放视频,
 * 验证可播放性并读取真实媒体信息 (分辨率/时长/编码/帧率/码率).
 *
 * 默认会弹出一个 Compose 测试窗口实时显示播放画面 (showWindow=false 可关;
 * 无窗口时依赖 mpv 模块的 frame-drain, 播放进度照常推进).
 *
 * 原生库来源 (dev): `-Dani.mpv.native.dir=<dir>` 或默认的 mediamp worktree dev-native 构建.
 */
class MpvVideoAnalyzer {
    class Output(
        val analysis: MediaAnalysisResult,
        val frames: List<CapturedFrame>,
    )

    suspend fun analyze(input: ProbeVideoInput): Output {
        runCatching {
            val nativeDir = System.getProperty("ani.mpv.native.dir")
            if (nativeDir != null) {
                MPVHandle.setRuntimeLibraryDirectory(nativeDir, extractRuntimeLibrary = false)
            } else {
                MPVHandle.useDefaultRuntimeLibraryDirectory()
            }
            MPVHandle.setLogHandler { msg ->
                if (msg.level <= 30) println("[mpv/" + msg.prefix + "] " + msg.line) // warn+
            }
        }.onFailure { exception ->
            return Output(
                MediaAnalysisResult(
                    available = false,
                    errors = listOf(
                        "mpv 原生库加载失败: ${exception.message.orEmpty()}. " +
                                "构建: ./gradlew :mediamp-mpv:compileJniDevMacos, 或 -Dani.mpv.native.dir 指定目录",
                    ),
                ),
                emptyList(),
            )
        }
        // 播放器要求单线程调用
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mpv-video-analyzer").apply { isDaemon = true }
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
        val player = MpvMediampPlayer(Any(), coroutineContext)
        val window = if (input.showWindow) createWindow(player, input.videoUrl) else null
        return try {
            val playbackTest = runPlaybackTest(player, input, errors, frames)

            val handle = player.impl as MPVHandle
            // 轨道参数要等 demux/解码就绪才可用, 轮询等待
            withTimeoutOrNull(10_000) {
                while (handle.getPropertyInt("width") <= 0) {
                    delay(200)
                }
            }

            val width = handle.getPropertyInt("width").takeIf { it > 0 }
            val height = handle.getPropertyInt("height").takeIf { it > 0 }
            val videoCodec = handle.getPropertyString("video-format")
            val audioCodec = handle.getPropertyString("audio-codec-name")
            val fps = handle.getPropertyDouble("container-fps").takeIf { it > 0 }
            val videoBitrate = handle.getPropertyInt("video-bitrate").takeIf { it > 0 }?.toLong()
            val audioBitrate = handle.getPropertyInt("audio-bitrate").takeIf { it > 0 }?.toLong()
            val sampleRate = handle.getPropertyInt("audio-params/samplerate").takeIf { it > 0 }
            val channels = handle.getPropertyInt("audio-params/channel-count").takeIf { it > 0 }
            val hwdec = handle.getPropertyString("hwdec-current")

            val durationMillis = player.getCurrentMediaProperties()?.durationMillis?.takeIf { it > 0 }

            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "animeko-player (mpv, hwdec=${hwdec ?: "no"})",
                    durationSeconds = durationMillis?.let { it / 1000.0 },
                    overallBitrate = listOfNotNull(videoBitrate, audioBitrate).takeIf { it.isNotEmpty() }?.sum(),
                    video = if (videoCodec != null || width != null) {
                        VideoStreamInfo(
                            codec = videoCodec?.lowercase(),
                            width = width,
                            height = height,
                            frameRate = fps?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') },
                            bitrate = videoBitrate,
                        )
                    } else null,
                    audio = if (audioCodec != null) {
                        AudioStreamInfo(
                            codec = audioCodec.lowercase(),
                            sampleRate = sampleRate?.toString(),
                            channels = channels,
                            bitrate = audioBitrate,
                        )
                    } else null,
                    playback = playbackTest,
                    errors = errors,
                ),
                frames,
            )
        } catch (e: Exception) {
            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "animeko-player (mpv)",
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
        player: MpvMediampPlayer,
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
     * 用 mpv 的 screenshot-to-file (video 模式, 不含 OSD) 抓当前解码帧存为 PNG.
     * 命令异步执行, 轮询等待文件生成.
     */
    private suspend fun captureFrame(
        player: MpvMediampPlayer,
        dir: File,
        label: String,
    ): CapturedFrame? {
        val file = dir.resolve("$label.png")
        val positionMillis = player.getCurrentPositionMillis()
        player.features[Screenshots.Key]?.takeScreenshot(file.absolutePath) ?: return null
        repeat(25) {
            if (file.isFile && file.length() > 0) {
                return CapturedFrame(
                    positionMillis = positionMillis,
                    path = file.absolutePath,
                    label = label,
                )
            }
            delay(200)
        }
        return null
    }

    private fun createWindow(player: MpvMediampPlayer, videoUrl: String): ComposeWindow {
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Animeko 数据源测试 - probe_video (mpv)"
                size = Dimension(960, 600)
                setLocationRelativeTo(null)
                setContent { ProbeWindowContent(player, videoUrl) }
                isVisible = true
            }
        }
        return window
    }
}

@Composable
private fun ProbeWindowContent(player: MpvMediampPlayer, videoUrl: String) {
    val state by player.playbackState.collectAsState()
    val properties by player.mediaProperties.collectAsState()
    val position by player.currentPositionMillis.collectAsState()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MpvMediampPlayerSurface(player, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp)) {
            Text(
                "probe_video: 用 Animeko 播放器 (mpv) 真实播放测试",
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
