/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 视频能力: 用 ffprobe 读取真实的媒体信息 (容器格式/码率/分辨率/时长),
 * 并用 ffmpeg 实际解码开头几秒来验证可播放性 (与播放器相同的 demux + decode 路径).
 *
 * 二进制查找顺序: 工具参数 > `ANI_MCP_FFPROBE`/`ANI_MCP_FFMPEG` 环境变量 > PATH.
 * 找不到时返回 `available = false`, 调用方可以退回纯 HTTP 探测.
 */
class FfmpegVideoAnalyzer(
    private val json: Json,
) {
    suspend fun analyze(input: ProbeVideoInput): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val ffprobe = findExecutable(input.ffprobePath, "ANI_MCP_FFPROBE", "ffprobe")
            ?: return@withContext MediaAnalysisResult(
                available = false,
                errors = listOf("找不到 ffprobe. 可通过 ffprobePath 参数或 ANI_MCP_FFPROBE 环境变量指定, 或安装到 PATH (brew install ffmpeg)."),
            )

        val errors = mutableListOf<String>()
        val probeOutput = runCommand(
            buildList {
                add(ffprobe)
                add("-v")
                add("error")
                add("-print_format")
                add("json")
                add("-show_format")
                add("-show_streams")
                addAll(headerArgs(input.headers))
                add(input.videoUrl)
            },
            timeoutMillis = input.analyzeTimeoutMillis,
        )

        if (probeOutput.exitCode != 0) {
            return@withContext MediaAnalysisResult(
                available = true,
                tool = ffprobe,
                errors = listOf(
                    "ffprobe 退出码 ${probeOutput.exitCode}: ${probeOutput.stderr.take(2000)}",
                ),
            )
        }

        val parsed = runCatching { parseFfprobeOutput(probeOutput.stdout) }
            .getOrElse { exception ->
                errors += "ffprobe 输出解析失败: ${exception.message.orEmpty()}"
                null
            }

        val decodeTest = if (input.decodeTest) {
            runDecodeTest(input, errors)
        } else {
            null
        }

        MediaAnalysisResult(
            available = true,
            tool = ffprobe,
            containerFormat = parsed?.containerFormat,
            durationSeconds = parsed?.durationSeconds,
            overallBitrate = parsed?.overallBitrate,
            video = parsed?.video,
            audio = parsed?.audio,
            decodeTest = decodeTest,
            errors = errors,
        )
    }

    private fun runDecodeTest(input: ProbeVideoInput, errors: MutableList<String>): DecodeTestResult {
        val ffmpeg = findExecutable(input.ffmpegPath, "ANI_MCP_FFMPEG", "ffmpeg")
        if (ffmpeg == null) {
            errors += "找不到 ffmpeg, 跳过解码测试"
            return DecodeTestResult(ran = false, ok = false, errors = listOf("ffmpeg not found"))
        }
        val seconds = input.decodeDurationSeconds.coerceIn(1, 60)
        val result = runCommand(
            buildList {
                add(ffmpeg)
                add("-v")
                add("error")
                add("-nostdin")
                addAll(headerArgs(input.headers))
                add("-i")
                add(input.videoUrl)
                add("-t")
                add(seconds.toString())
                add("-f")
                add("null")
                add("-")
            },
            timeoutMillis = input.analyzeTimeoutMillis,
        )
        val decodeErrors = result.stderr.lines().filter { it.isNotBlank() }.take(20)
        return DecodeTestResult(
            ran = true,
            ok = result.exitCode == 0,
            requestedSeconds = seconds,
            errors = if (result.exitCode == 0) emptyList() else decodeErrors + "ffmpeg 退出码 ${result.exitCode}",
        )
    }

    // region ffprobe output parsing

    class ParsedFfprobeOutput(
        val containerFormat: String?,
        val durationSeconds: Double?,
        val overallBitrate: Long?,
        val video: VideoStreamInfo?,
        val audio: AudioStreamInfo?,
    )

    fun parseFfprobeOutput(stdout: String): ParsedFfprobeOutput {
        val root = json.parseToJsonElement(stdout).jsonObject
        val format = root["format"]?.jsonObject
        val streams = root["streams"]?.jsonArray?.map { it.jsonObject }.orEmpty()

        val videoStream = streams.firstOrNull { it.stringOrNull("codec_type") == "video" }
        val audioStream = streams.firstOrNull { it.stringOrNull("codec_type") == "audio" }

        return ParsedFfprobeOutput(
            containerFormat = format?.stringOrNull("format_name"),
            durationSeconds = format?.stringOrNull("duration")?.toDoubleOrNull()
                ?: videoStream?.stringOrNull("duration")?.toDoubleOrNull(),
            overallBitrate = format?.stringOrNull("bit_rate")?.toLongOrNull(),
            video = videoStream?.let {
                VideoStreamInfo(
                    codec = it.stringOrNull("codec_name"),
                    width = it.stringOrNull("width")?.toIntOrNull(),
                    height = it.stringOrNull("height")?.toIntOrNull(),
                    frameRate = it.stringOrNull("avg_frame_rate")?.takeIf { rate -> rate != "0/0" },
                    bitrate = it.stringOrNull("bit_rate")?.toLongOrNull(),
                )
            },
            audio = audioStream?.let {
                AudioStreamInfo(
                    codec = it.stringOrNull("codec_name"),
                    sampleRate = it.stringOrNull("sample_rate"),
                    channels = it.stringOrNull("channels")?.toIntOrNull(),
                    bitrate = it.stringOrNull("bit_rate")?.toLongOrNull(),
                )
            },
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return this[key]?.jsonPrimitive?.content
    }

    // endregion

    // region process helpers

    private class CommandOutput(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runCommand(command: List<String>, timeoutMillis: Long): CommandOutput {
        val process = ProcessBuilder(command).start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader = Thread { process.inputStream.bufferedReader().copyTo(stdout) }.apply { start() }
        val stderrReader = Thread { process.errorStream.bufferedReader().copyTo(stderr) }.apply { start() }

        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        stdoutReader.join(5_000)
        stderrReader.join(5_000)

        return if (finished) {
            CommandOutput(exitCode = process.exitValue(), stdout = stdout.toString(), stderr = stderr.toString())
        } else {
            CommandOutput(
                exitCode = -1,
                stdout = stdout.toString(),
                stderr = "$stderr\n(timed out after ${timeoutMillis}ms)",
            )
        }
    }

    private fun java.io.Reader.copyTo(target: StringBuilder) {
        try {
            val buffer = CharArray(8192)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                target.append(buffer, 0, read)
            }
        } catch (_: Exception) {
            // process killed; keep what we have
        }
    }

    private fun headerArgs(headers: Map<String, String>): List<String> {
        if (headers.isEmpty()) return emptyList()
        val headerBlock = headers.entries.joinToString("\r\n", postfix = "\r\n") { (key, value) -> "$key: $value" }
        return listOf("-headers", headerBlock)
    }

    private fun findExecutable(explicitPath: String?, envVar: String, name: String): String? {
        explicitPath?.takeIf { it.isNotBlank() }?.let { path ->
            return if (File(path).canExecute()) path else null
        }
        System.getenv(envVar)?.takeIf { it.isNotBlank() }?.let { path ->
            if (File(path).canExecute()) return path
        }
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        for (dir in pathDirs) {
            val candidate = File(dir, name)
            if (candidate.canExecute()) return candidate.absolutePath
        }
        // PATH 在 GUI 启动的 MCP 客户端中通常不含 homebrew
        for (fallback in listOf("/opt/homebrew/bin/$name", "/usr/local/bin/$name", "/usr/bin/$name")) {
            if (File(fallback).canExecute()) return fallback
        }
        return null
    }

    // endregion
}
